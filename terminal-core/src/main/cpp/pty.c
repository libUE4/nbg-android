#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pty.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define TAG "NbgPty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static char **read_string_array(JNIEnv *env, jobjectArray array) {
  int len = (*env)->GetArrayLength(env, array);
  char **out = (char **)calloc((size_t)len + 1, sizeof(char *));
  for (int i = 0; i < len; i++) {
    jstring item = (jstring)(*env)->GetObjectArrayElement(env, array, i);
    const char *raw = (*env)->GetStringUTFChars(env, item, 0);
    out[i] = strdup(raw);
    (*env)->ReleaseStringUTFChars(env, item, raw);
    (*env)->DeleteLocalRef(env, item);
  }
  out[len] = NULL;
  return out;
}

static void free_string_array(char **array) {
  if (array == NULL) return;
  for (int i = 0; array[i] != NULL; i++) free(array[i]);
  free(array);
}

JNIEXPORT jintArray JNICALL
Java_com_nbg_android_terminal_PtyProcess_00024Companion_createSubprocess(
    JNIEnv *env,
    jobject thiz,
    jobjectArray cmdarray,
    jobjectArray envarray,
    jstring workingDir,
    jint rows,
    jint cols) {
  (void)thiz;
  int master_fd = -1;
  const char *cwd_raw = (*env)->GetStringUTFChars(env, workingDir, 0);
  char *cwd = strdup(cwd_raw);
  (*env)->ReleaseStringUTFChars(env, workingDir, cwd_raw);

  char **argv = read_string_array(env, cmdarray);
  char **envp = read_string_array(env, envarray);

  struct termios tt;
  memset(&tt, 0, sizeof(tt));
  tt.c_iflag = ICRNL | IXON | IXANY;
  tt.c_oflag = OPOST | ONLCR;
  tt.c_lflag = ISIG | ICANON | ECHO | ECHOE | ECHOK | ECHONL | IEXTEN;
  tt.c_cflag = CS8 | CREAD;
  tt.c_cc[VINTR] = 'C' - '@';
  tt.c_cc[VEOF] = 'D' - '@';
  tt.c_cc[VMIN] = 1;
  tt.c_cc[VTIME] = 0;

  struct winsize ws;
  ws.ws_row = rows < 1 ? 1 : rows;
  ws.ws_col = cols < 1 ? 1 : cols;
  ws.ws_xpixel = 0;
  ws.ws_ypixel = 0;

  pid_t pid = forkpty(&master_fd, NULL, &tt, &ws);
  if (pid < 0) {
    LOGE("forkpty failed: %s", strerror(errno));
    free(cwd);
    free_string_array(argv);
    free_string_array(envp);
    return NULL;
  }

  if (pid == 0) {
    if (chdir(cwd) != 0) {
      fprintf(stderr, "chdir(%s) failed: %s\n", cwd, strerror(errno));
      _exit(1);
    }
    execve(argv[0], argv, envp);
    fprintf(stderr, "execve(%s) failed: %s\n", argv[0], strerror(errno));
    _exit(1);
  }

  jintArray result = (*env)->NewIntArray(env, 2);
  jint fill[2];
  fill[0] = pid;
  fill[1] = master_fd;
  (*env)->SetIntArrayRegion(env, result, 0, 2, fill);

  free(cwd);
  free_string_array(argv);
  free_string_array(envp);
  return result;
}

JNIEXPORT jint JNICALL
Java_com_nbg_android_terminal_PtyProcess_00024Companion_waitForPid(
    JNIEnv *env,
    jobject thiz,
    jint pid) {
  (void)env;
  (void)thiz;
  int status = 0;
  waitpid(pid, &status, 0);
  if (WIFEXITED(status)) return WEXITSTATUS(status);
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_nbg_android_terminal_PtyProcess_setPtyWindowSize(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jint pid,
    jint rows,
    jint cols) {
  (void)env;
  (void)thiz;
  struct winsize ws;
  ws.ws_row = rows;
  ws.ws_col = cols;
  ws.ws_xpixel = 0;
  ws.ws_ypixel = 0;
  int result = ioctl(fd, TIOCSWINSZ, &ws);
  if (result == 0) {
    // Notify the foreground process group of the PTY so full-screen TUIs
    // (which run as children of the launched shell) receive SIGWINCH.
    pid_t fg_pgrp = tcgetpgrp(fd);
    if (fg_pgrp > 0) {
      kill(-fg_pgrp, SIGWINCH);
    }
    // Also signal the shell's own process group as a fallback. Children that
    // share the shell's group (our `bash -c "... && start_shell"` case) get it.
    if (pid > 0) {
      kill(-pid, SIGWINCH);
      kill(pid, SIGWINCH);
    }
  }
  return result;
}
