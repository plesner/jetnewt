package com.jetnewt.geo;
/**
 * A mapping between geographic and unit lat/lngs.
 */
public class CoordinateConverter {

  private final OrdinateConverter latMap = new OrdinateConverter(-90);
  private final OrdinateConverter lngMap = new OrdinateConverter(180);

  private double unitToGeoLat(double unit) {
    return this.latMap.fromUnit(unit);
  }

  private double unitToGeoLng(double unit) {
    return this.lngMap.fromUnit(unit);
  }

  public GeoLatLng unitToGeo(UnitLatLng unit) {
    return new GeoLatLng(
        unitToGeoLat(unit.getUnitLat()),
        unitToGeoLng(unit.getUnitLng()));
  }

  public GeoLatLngRect unitToGeo(UnitLatLngRect unit) {
    return new GeoLatLngRect(
        unitToGeo(unit.getSouthWest()),
        unitToGeo(unit.getNorthEast()));
  }

  private double geoToUnitLat(double geoLat) {
    return this.latMap.toUnit(geoLat);
  }

  private double geoToUnitLng(double geoLng) {
    return this.lngMap.toUnit(geoLng);
  }

  public UnitLatLng geoToUnit(GeoLatLng geo) {
    return new UnitLatLng(
        geoToUnitLat(geo.getGeoLat()),
        geoToUnitLng(geo.getGeoLng()));
  }

  public OrdinateConverter getLatMap() {
    return this.latMap;
  }

  public OrdinateConverter getLngMap() {
    return this.lngMap;
  }

  private static final CoordinateConverter INSTANCE = new CoordinateConverter();

  public static CoordinateConverter global() {
    return INSTANCE;
  }

}
