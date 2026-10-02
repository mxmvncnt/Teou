package main

import (
	"context"
	"log/slog"
	"os"

	"fcm-backend/config"

	firebase "firebase.google.com/go/v4"
	"google.golang.org/api/option"
)

func main() {
	ctx := context.Background()
	var opts []option.ClientOption
	if config.FirebaseCredentialsFile != "" {
		opts = append(opts, option.WithCredentialsFile(config.FirebaseCredentialsFile))
	}
	app, err := firebase.NewApp(ctx, nil, opts...)
	if err != nil {
		slog.Error("initialize Firebase", "error", err)
		os.Exit(1)
	}

	if _, err := app.Messaging(ctx); err != nil {
		slog.Error("initialize Firebase Messaging", "error", err)
		os.Exit(1)
	}

	slog.Info("Firebase Messaging initialized")
}
