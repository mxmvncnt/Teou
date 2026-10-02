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

// ProjectName config

var ProjectName = thisOrThat(os.Getenv("PROJECT_NAME"), "backbone")

// Logging config

var LogLevel = getLogLevel(os.Getenv("LOG_LEVEL"))

// Web server config

var ServerHostname = thisOrThat(os.Getenv("SERVER_HOST"), "localhost")
var ServerPort = thisOrThat(os.Getenv("SERVER_PORT"), "8080")
var FrontendUrl = thisOrThat(os.Getenv("FRONTEND_URL"), "localhost")

// Database config

var DatabaseHost = thisOrThat(os.Getenv("DB_HOST"), "localhost")
var DatabasePort = thisOrThat(os.Getenv("DB_PORT"), "5432")
var DatabaseUsername = thisOrThat(os.Getenv("DB_USER"), "username")
var DatabasePassword = thisOrThat(os.Getenv("DB_PASS"), "password")
var DatabaseName = thisOrThat(os.Getenv("DB_NAME"), "mydb")
var DatabaseURL = "postgres://" + DatabaseUsername + ":" + DatabasePassword + "@" + DatabaseHost + ":" + DatabasePort + "/" + DatabaseName

// Passkey server config

var PasskeyServerURL = thisOrThat(os.Getenv("PASSKEY_SERVER_URL"), "http://localhost:9091")

// Resend email provider config

var ResendApiKey = thisOrThat(os.Getenv("RESEND_API_KEY"), "")

var JWTSigningKey = []byte(thisOrThat(os.Getenv("JWT_SIGNING_KEY"), ""))

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
