#include <android/log.h>
#include <jni.h>
#include <sampling.h>
#include <string>
#include <sstream>
#include <unistd.h>

#include "chat.h"
#include "common.h"
#include "llama.h"

namespace {
constexpr const char * LOG_TAG = "PrismGroveRuntime";
constexpr int N_THREADS_MIN = 2;
constexpr int N_THREADS_MAX = 4;
constexpr int N_THREADS_HEADROOM = 2;
constexpr int DEFAULT_CONTEXT_SIZE = 8192;
constexpr int OVERFLOW_HEADROOM = 4;
constexpr int BATCH_SIZE = 512;
constexpr float DEFAULT_TEMPERATURE = 0.5f;
constexpr int DEFAULT_TOP_K = 20;
constexpr float DEFAULT_TOP_P = 0.9f;

constexpr const char * ROLE_SYSTEM = "system";
constexpr const char * ROLE_USER = "user";
constexpr const char * ROLE_ASSISTANT = "assistant";

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_batch g_batch = {};
common_chat_templates_ptr g_chat_templates;
common_sampler * g_sampler = nullptr;

int g_context_size = DEFAULT_CONTEXT_SIZE;
int g_thread_count = 0;
float g_temperature = DEFAULT_TEMPERATURE;
int g_top_k = DEFAULT_TOP_K;
float g_top_p = DEFAULT_TOP_P;

std::vector<common_chat_msg> g_chat_messages;
llama_pos g_system_prompt_position = 0;
llama_pos g_current_position = 0;
llama_pos g_stop_generation_position = 0;
std::string g_cached_utf8_bytes;
std::ostringstream g_assistant_stream;

bool is_valid_utf8(const char * text) {
    if (!text) {
        return true;
    }
    const auto * bytes = reinterpret_cast<const unsigned char *>(text);
    while (*bytes != 0x00) {
        int count = 0;
        if ((*bytes & 0x80) == 0x00) {
            count = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            count = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            count = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            count = 4;
        } else {
            return false;
        }
        ++bytes;
        for (int i = 1; i < count; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            ++bytes;
        }
    }
    return true;
}

void reset_short_term_state() {
    g_stop_generation_position = 0;
    g_cached_utf8_bytes.clear();
    g_assistant_stream.str("");
    g_assistant_stream.clear();
}

void reset_long_term_state(bool clear_kv_cache = true) {
    g_chat_messages.clear();
    g_system_prompt_position = 0;
    g_current_position = 0;
    if (clear_kv_cache && g_context != nullptr) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

int effective_threads() {
    if (g_thread_count > 0) {
        return g_thread_count;
    }
    const int online = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, online - N_THREADS_HEADROOM));
}

void rebuild_sampler() {
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    common_params_sampling params;
    params.temp = g_temperature;
    params.top_k = g_top_k;
    params.top_p = g_top_p;
    g_sampler = common_sampler_init(g_model, params);
}

llama_context * init_context(llama_model * model, int context_length) {
    if (model == nullptr) {
        return nullptr;
    }
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = context_length;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = effective_threads();
    ctx_params.n_threads_batch = effective_threads();
    LOGI("Initializing context with n_ctx=%d threads=%d", context_length, ctx_params.n_threads);
    return llama_init_from_model(model, ctx_params);
}

void shift_context() {
    const int discard_count = (g_current_position - g_system_prompt_position) / 2;
    if (discard_count <= 0) {
        return;
    }
    llama_memory_seq_rm(
        llama_get_memory(g_context),
        0,
        g_system_prompt_position,
        g_system_prompt_position + discard_count
    );
    llama_memory_seq_add(
        llama_get_memory(g_context),
        0,
        g_system_prompt_position + discard_count,
        g_current_position,
        -discard_count
    );
    g_current_position -= discard_count;
}

std::string fallback_format_message(const std::string & role, const std::string & content) {
    if (role == ROLE_SYSTEM) {
        return "System: " + content + "\n";
    }
    if (role == ROLE_ASSISTANT) {
        return "Assistant: " + content + "\n";
    }
    return "User: " + content + "\n";
}

std::string add_and_format_chat_message(const std::string & role, const std::string & content) {
    common_chat_msg message;
    message.role = role;
    message.content = content;
    auto formatted = common_chat_format_single(
        g_chat_templates.get(),
        g_chat_messages,
        message,
        role == ROLE_USER,
        false
    );
    g_chat_messages.push_back(message);
    return formatted;
}

int decode_tokens(
    const llama_tokens & tokens,
    llama_pos start_pos,
    bool compute_last_logit
) {
    for (int i = 0; i < static_cast<int>(tokens.size()); i += BATCH_SIZE) {
        const int chunk_size = std::min(static_cast<int>(tokens.size()) - i, BATCH_SIZE);
        common_batch_clear(g_batch);

        if (start_pos + i + chunk_size >= g_context_size - OVERFLOW_HEADROOM) {
            shift_context();
        }

        for (int j = 0; j < chunk_size; ++j) {
            const bool want_logit = compute_last_logit && (i + j == static_cast<int>(tokens.size()) - 1);
            common_batch_add(g_batch, tokens[i + j], start_pos + i + j, {0}, want_logit);
        }
        const int result = llama_decode(g_context, g_batch);
        if (result != 0) {
            LOGE("llama_decode failed: %d", result);
            return result;
        }
    }
    return 0;
}

int append_text_message(const std::string & role, const std::string & content) {
    if (content.empty()) {
        return 0;
    }
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted = has_chat_template
        ? add_and_format_chat_message(role, content)
        : fallback_format_message(role, content);
    auto tokens = common_tokenize(g_context, formatted, has_chat_template, has_chat_template);
    const int max_batch_size = g_context_size - OVERFLOW_HEADROOM;
    if (static_cast<int>(tokens.size()) > max_batch_size) {
        tokens.resize(max_batch_size);
    }
    const int result = decode_tokens(tokens, g_current_position, false);
    if (result != 0) {
        return result;
    }
    g_current_position += static_cast<int>(tokens.size());
    if (role == ROLE_SYSTEM) {
        g_system_prompt_position = g_current_position;
    }
    return 0;
}
}

extern "C"
JNIEXPORT void JNICALL
Java_com_prismml_grove_runtime_NativeBridge_init(
    JNIEnv * env,
    jobject,
    jstring native_lib_dir
) {
    llama_log_set([](enum ggml_log_level level, const char * text, void *) {
        switch (level) {
            case GGML_LOG_LEVEL_ERROR: LOGE("%s", text); break;
            case GGML_LOG_LEVEL_WARN: LOGW("%s", text); break;
            default: LOGI("%s", text); break;
        }
    }, nullptr);

    const char * path = env->GetStringUTFChars(native_lib_dir, 0);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(native_lib_dir, path);
    llama_backend_init();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_prismml_grove_runtime_NativeBridge_load(
    JNIEnv * env,
    jobject,
    jstring model_path
) {
    const char * path = env->GetStringUTFChars(model_path, 0);
    llama_model_params params = llama_model_default_params();
    auto * model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (model == nullptr) {
        return 1;
    }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_prismml_grove_runtime_NativeBridge_prepare(
    JNIEnv *,
    jobject,
    jint context_length,
    jint thread_count,
    jfloat temperature,
    jint top_k,
    jfloat top_p
) {
    g_context_size = context_length > 0 ? context_length : DEFAULT_CONTEXT_SIZE;
    g_thread_count = thread_count;
    g_temperature = temperature > 0 ? temperature : DEFAULT_TEMPERATURE;
    g_top_k = top_k > 0 ? top_k : DEFAULT_TOP_K;
    g_top_p = top_p > 0 ? top_p : DEFAULT_TOP_P;

    g_context = init_context(g_model, g_context_size);
    if (g_context == nullptr) {
        return 1;
    }
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    rebuild_sampler();
    reset_long_term_state(false);
    reset_short_term_state();
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_prismml_grove_runtime_NativeBridge_systemInfo(JNIEnv * env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_prismml_grove_runtime_NativeBridge_reset(
    JNIEnv * env,
    jobject,
    jstring system_prompt
) {
    reset_long_term_state();
    reset_short_term_state();
    rebuild_sampler();

    const char * text = env->GetStringUTFChars(system_prompt, 0);
    std::string prompt(text);
    env->ReleaseStringUTFChars(system_prompt, text);
    if (prompt.empty()) {
        return 0;
    }
    return append_text_message(ROLE_SYSTEM, prompt);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_prismml_grove_runtime_NativeBridge_append(
    JNIEnv * env,
    jobject,
    jstring role,
    jstring content
) {
    const char * role_chars = env->GetStringUTFChars(role, 0);
    const char * content_chars = env->GetStringUTFChars(content, 0);
    std::string role_text(role_chars);
    std::string content_text(content_chars);
    env->ReleaseStringUTFChars(role, role_chars);
    env->ReleaseStringUTFChars(content, content_chars);
    return append_text_message(role_text, content_text);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_prismml_grove_runtime_NativeBridge_beginCompletion(
    JNIEnv * env,
    jobject,
    jstring user_prompt,
    jint predict_length
) {
    reset_short_term_state();

    const char * text = env->GetStringUTFChars(user_prompt, 0);
    std::string prompt(text);
    env->ReleaseStringUTFChars(user_prompt, text);

    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted = has_chat_template
        ? add_and_format_chat_message(ROLE_USER, prompt)
        : fallback_format_message(ROLE_USER, prompt);
    auto tokens = common_tokenize(g_context, formatted, has_chat_template, has_chat_template);
    const int max_batch_size = g_context_size - OVERFLOW_HEADROOM;
    if (static_cast<int>(tokens.size()) > max_batch_size) {
        tokens.resize(max_batch_size);
    }
    const int result = decode_tokens(tokens, g_current_position, true);
    if (result != 0) {
        return result;
    }
    g_current_position += static_cast<int>(tokens.size());
    g_stop_generation_position = g_current_position + predict_length;
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_prismml_grove_runtime_NativeBridge_generateNextToken(JNIEnv * env, jobject) {
    if (g_context == nullptr || g_model == nullptr) {
        return nullptr;
    }
    if (g_current_position >= g_context_size - OVERFLOW_HEADROOM) {
        shift_context();
    }
    if (g_current_position >= g_stop_generation_position) {
        return nullptr;
    }

    const auto token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, token_id, true);

    common_batch_clear(g_batch);
    common_batch_add(g_batch, token_id, g_current_position, {0}, true);
    if (llama_decode(g_context, g_batch) != 0) {
        return nullptr;
    }
    ++g_current_position;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token_id)) {
        if (!g_assistant_stream.str().empty()) {
            if (common_chat_templates_was_explicit(g_chat_templates.get())) {
                add_and_format_chat_message(ROLE_ASSISTANT, g_assistant_stream.str());
            } else {
                g_chat_messages.push_back({ROLE_ASSISTANT, g_assistant_stream.str()});
            }
        }
        return nullptr;
    }

    g_cached_utf8_bytes += common_token_to_piece(g_context, token_id);
    if (!is_valid_utf8(g_cached_utf8_bytes.c_str())) {
        return env->NewStringUTF("");
    }

    g_assistant_stream << g_cached_utf8_bytes;
    jstring result = env->NewStringUTF(g_cached_utf8_bytes.c_str());
    g_cached_utf8_bytes.clear();
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_prismml_grove_runtime_NativeBridge_unload(JNIEnv *, jobject) {
    reset_long_term_state(false);
    reset_short_term_state();
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    if (g_batch.token != nullptr) {
        llama_batch_free(g_batch);
        g_batch = {};
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_prismml_grove_runtime_NativeBridge_shutdown(JNIEnv *, jobject) {
    llama_backend_free();
}
