package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pMessageFactoryTest {

    @Test
    fun locRequest_hasLocRequestType() {
        assertEquals("pos_req", P2pMessageFactory.type(P2pMessageFactory.locRequest()))
    }

    @Test
    fun locResponse_roundTrip() {
        val bytes = P2pMessageFactory.locResponse(45.123456, 11.654321, 12.0)

        assertEquals("pos_res", P2pMessageFactory.type(bytes))
        val parsed = P2pMessageFactory.parseLocResponse(bytes)!!
        assertEquals(45.123456, parsed.lat, 1e-9)
        assertEquals(11.654321, parsed.lon, 1e-9)
        assertEquals(12.0, parsed.accuracy, 1e-9)
    }

    @Test
    fun type_returnsNullOnGarbage() {
        assertNull(P2pMessageFactory.type("not json".toByteArray()))
    }

    @Test
    fun parseLocResponse_rejectsWrongType() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locRequest()))
    }

    @Test
    fun locRequest_carriesTimestamp() {
        assertEquals(123456789L, P2pMessageFactory.timestamp(P2pMessageFactory.locRequest(123456789L)))
    }

    @Test
    fun locResponse_carriesTimestamp() {
        val bytes = P2pMessageFactory.locResponse(1.0, 2.0, 3.0, 987654321L)
        assertEquals(987654321L, P2pMessageFactory.timestamp(bytes))
    }

    @Test
    fun timestamp_returnsNullWhenAbsent() {
        assertNull(P2pMessageFactory.timestamp("{\"type\":\"pos_req\"}".toByteArray()))
    }

    @Test
    fun parseLocResponse_rejectsOutOfRangeLatitude() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(91.0, 0.0, 1.0)))
    }

    @Test
    fun parseLocResponse_rejectsOutOfRangeLongitude() {
        assertNull(P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(0.0, 181.0, 1.0)))
    }

    @Test
    fun parseLocResponse_acceptsBoundaryCoordinates() {
        val parsed = P2pMessageFactory.parseLocResponse(P2pMessageFactory.locResponse(-90.0, 180.0, 5.0))!!
        assertEquals(-90.0, parsed.lat, 1e-9)
        assertEquals(180.0, parsed.lon, 1e-9)
    }

}
