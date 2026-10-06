package com.meloxu.cmanwp.model;

import ucar.nc2.grib.grib2.Grib2Record;

import java.nio.file.Path;

public record CmaNwpMessage(Grib2Record record, Path file, int index, String source,
                            String inittime, String forecast, String shortName,
                            int levelType, double level) {
}
