package com.meloxu.cmanwp.model;

public class LatLngBounds {
    public LatLng _southWest;
    public LatLng _northEast;

    public LatLngBounds() {
    }

    public LatLngBounds(LatLng southWest, LatLng northEast) {
        this._southWest = southWest;
        this._northEast = northEast;
    }
}
