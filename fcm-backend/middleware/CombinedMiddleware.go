// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package middleware

import "net/http"

func Combined(h func(http.ResponseWriter, *http.Request) error) http.HandlerFunc {
	return LoggingMiddleware(ErrorHandler(h))
}
