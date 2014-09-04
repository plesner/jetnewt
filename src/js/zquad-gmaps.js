(function (namespace) {

if (!namespace.zquad || namespace.zquad.gmaps)
  return;
var zquad = namespace.zquad;
var gmaps = {};
zquad.gmaps = gmaps;

zquad.ZQuad.prototype.getCenter = function() {
  return new google.maps.LatLng(this.getCenterLat(), this.getCenterLng());
};

zquad.ZQuad.prototype.getBounds = function() {
  var lat = this.getCornerLat();
  var lng = this.getCornerLng();
  var width = this.getWidth();
  var height = this.getHeight();
  return new google.maps.LatLngBounds(
    new google.maps.LatLng(lat, lng),
    new google.maps.LatLng(lat - height, lng + width)
  );
};

})(this);
