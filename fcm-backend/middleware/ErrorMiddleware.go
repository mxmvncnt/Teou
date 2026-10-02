// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package middleware

import (
	"errors"
	"net/http"

	"fcm-backend/utils/apierror"
	"fcm-backend/utils/logger"
)

func ErrorHandler(h func(http.ResponseWriter, *http.Request) error) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := h(w, r); err != nil {
			var apiErr *apierror.ApiError
			if !errors.As(err, &apiErr) {
				apiErr = apierror.NewApiError(500, "UNEXPECTED_ERROR", "An internal error occurred", "")
			}
			logger.Error(apiErr.Error())
			apiErr.Send(w)
		}
	}
}
