package config

import (
	"os"
)

const (
	LogLevelDebug = 0
	LogLevelInfo  = 1
	LogLevelWarn  = 2
	LogLevelError = 3
	LogLevelFatal = 4
)

// Config config (lol)

var ConfigName = thisOrThat(os.Getenv("CONFIG_NAME"), "DEFAULT-NO-CONFIG")

// Firebase config

var FirebaseCredentialsFile = thisOrThat(os.Getenv("GOOGLE_APPLICATION_CREDENTIALS"), "./google-credentials.json")

// Logging config

var LogLevel = getLogLevel(os.Getenv("LOG_LEVEL"))

// Web server config

var ServerHostname = thisOrThat(os.Getenv("SERVER_HOST"), "localhost")
var ServerPort = thisOrThat(os.Getenv("SERVER_PORT"), "8080")

func thisOrThat(this, that string) string {
	if this != "" {
		return this
	}
	return that
}

func getLogLevel(level string) int {
	switch level {
	case "debug":
		return LogLevelDebug
	case "info":
		return LogLevelInfo
	case "warn":
		return LogLevelWarn
	case "error":
		return LogLevelError
	case "fatal":
		return LogLevelFatal
	}
	return LogLevelDebug
}
