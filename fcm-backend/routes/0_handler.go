package routes

import "firebase.google.com/go/v4/messaging"

type RoutesHandler struct {
	firebaseClient *messaging.Client
}

var Handler *RoutesHandler

func NewRoutesHandler(firebaseClient *messaging.Client) *RoutesHandler {
	return &RoutesHandler{firebaseClient: firebaseClient}
}
