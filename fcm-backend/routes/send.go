package routes

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"

	"fcm-backend/utils"
	"fcm-backend/utils/apierror"

	"firebase.google.com/go/v4/messaging"
)

func (handler *RoutesHandler) Send(w http.ResponseWriter, r *http.Request) error {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		var sizeError *http.MaxBytesError
		if errors.As(err, &sizeError) {
			return apierror.NewApiError(413, "REQUEST_TOO_LARGE", "Request body too large", "")
		}
		return apierror.NewApiError(400, "INVALID_REQUEST", "Could not read request", "")
	}

	var input struct {
		Token   string `json:"token"`
		Payload string `json:"payload"`
	}
	if json.Unmarshal(body, &input) != nil || input.Token == "" || input.Payload == "" {
		return apierror.NewApiError(400, "INVALID_REQUEST", "Expected JSON with token and payload", "")
	}

	data := map[string]string{"payload": input.Payload}
	encoded, _ := json.Marshal(data)
	if len(encoded) > 4096 {
		return apierror.NewApiError(413, "PAYLOAD_TOO_LARGE", "FCM payload too large", "")
	}

	ttl := 60 * time.Second
	id, err := handler.firebaseClient.Send(r.Context(), &messaging.Message{
		Token:   input.Token,
		Data:    data,
		Android: &messaging.AndroidConfig{Priority: "high", TTL: &ttl},
	})
	if err != nil {
		return apierror.NewApiError(503, "FCM_UNAVAILABLE", "FCM delivery unavailable", "")
	}

	utils.SendJsonResponse(w, http.StatusAccepted, map[string]string{"messageId": id})
	return nil
}
