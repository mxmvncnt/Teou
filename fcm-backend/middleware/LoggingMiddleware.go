// Adapted from mxmvncnt/go-backend-template; see NOTICE and LICENSE.
package middleware

import (
	"net/http"
	"time"

	"fcm-backend/utils/logger"
)

func LoggingMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		response := &customResponseWriter{ResponseWriter: w, statusCode: http.StatusOK}
		next.ServeHTTP(response, r)
		logger.Infof("%s %s status=%d duration=%s", r.Method, r.URL.Path, response.statusCode, time.Since(start))
	}
}

type customResponseWriter struct {
	http.ResponseWriter
	statusCode  int
	wroteHeader bool
}

func (w *customResponseWriter) WriteHeader(status int) {
	if !w.wroteHeader {
		w.statusCode = status
		w.wroteHeader = true
		w.ResponseWriter.WriteHeader(status)
	}
}

func (w *customResponseWriter) Write(body []byte) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	return w.ResponseWriter.Write(body)
}

func (w *customResponseWriter) Unwrap() http.ResponseWriter { return w.ResponseWriter }
