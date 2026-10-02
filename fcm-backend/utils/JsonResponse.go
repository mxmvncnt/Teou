// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package utils

import (
	"encoding/json"
	"net/http"
)

func SendJsonResponse(w http.ResponseWriter, statusCode int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(data)
}
