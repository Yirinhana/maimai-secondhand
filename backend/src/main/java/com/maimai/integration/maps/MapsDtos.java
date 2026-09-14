package com.maimai.integration.maps;

import java.util.List;

/** 地图 DTO。所有坐标的语义均为 GCJ-02。 */
public final class MapsDtos {

    private MapsDtos() {
    }

    public static final String COORDINATE_SYSTEM = "GCJ-02";

    /** 经度,纬度。 */
    public record Coordinate(double longitude, double latitude, String coordinateSystem) {
        public Coordinate(double longitude, double latitude) {this(longitude,latitude,COORDINATE_SYSTEM);}
    }

    public record GeoCodedLocation(String formattedAddress, Coordinate location) {
    }

    public record ReverseGeoResult(String formattedAddress, Coordinate location) {
    }

    public record Poi(String id, String name, String address, Coordinate location) {
    }

    public record AroundSearchResult(List<Poi> pois, String coordinateSystem) {
        public AroundSearchResult(List<Poi> pois) {this(pois,COORDINATE_SYSTEM);}
    }

    public record DrivingRoute(long distanceMeters, long durationSeconds, Coordinate origin, Coordinate destination) {
    }
}
