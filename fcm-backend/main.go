package main

import (
	"context"
	"errors"
	"net"
	"net/http"
	"time"

	"fcm-backend/config"
	"fcm-backend/middleware"
	"fcm-backend/routes"
	"fcm-backend/utils/logger"

	firebase "firebase.google.com/go/v4"
	"google.golang.org/api/option"
)

func main() {
	var opts []option.ClientOption
	opts = append(opts, option.WithAuthCredentialsFile(option.ServiceAccount, config.FirebaseCredentialsFile))

	app, err := firebase.NewApp(context.Background(), nil, opts...)
	if err != nil {
		logger.Fatalf("Initialize Firebase: %v", err)
	}

	firebaseClient, err := app.Messaging(context.Background())
	if err != nil {
		logger.Fatalf("Initialize Firebase Messaging: %v", err)
	}

	routes.Handler = routes.NewRoutesHandler(firebaseClient)
	router := http.NewServeMux()
	router.HandleFunc("POST /send", middleware.Combined(routes.Handler.Send))

	server := &http.Server{
		Addr:              net.JoinHostPort(config.ServerHostname, config.ServerPort),
		Handler:           http.MaxBytesHandler(router, 8*1024),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      20 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	logger.Info("Server started on http://" + server.Addr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Fatalf("HTTP server: %v", err)
	}
}
