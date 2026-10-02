// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package logger

import (
	"log"
	"os"

	"fcm-backend/config"
)

var (
	stdoutLog = log.New(os.Stdout, "", log.LstdFlags)
	stderrLog = log.New(os.Stderr, "", log.LstdFlags)
)

func Info(message string) { Infof("%s", message) }

func Infof(message string, args ...any) {
	if config.LogLevel <= config.LogLevelInfo {
		stdoutLog.Printf("[INFO] "+message, args...)
	}
}

func Error(message string) {
	if config.LogLevel <= config.LogLevelError {
		stderrLog.Printf("[ERROR] %s", message)
	}
}

func Fatalf(message string, args ...any) {
	stderrLog.Fatalf("[FATAL] "+message, args...)
}
