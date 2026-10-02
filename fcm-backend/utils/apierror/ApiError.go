// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package apierror

import (
	"fmt"
	"net/http"

	"fcm-backend/utils"
)

type ApiError struct {
	StatusCode int    `json:"-"`
	Code       string `json:"code"`
	Message    string `json:"message"`
	Reason     string `json:"reason,omitempty"`
}

func (e *ApiError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

func NewApiError(statusCode int, code, message, reason string) *ApiError {
	return &ApiError{StatusCode: statusCode, Code: code, Message: message, Reason: reason}
}

func (e *ApiError) Send(w http.ResponseWriter) {
	utils.SendJsonResponse(w, e.StatusCode, e)
}
