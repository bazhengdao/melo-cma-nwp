package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.model.LatLng;
import com.meloxu.cmanwp.model.LatLngBounds;
import com.meloxu.cmanwp.model.NormalData;
import com.meloxu.cmanwp.model.NormalInfo;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Tile generation following the boxer TMS layout and sampling rules.
 */
@Service
public class CmaNwpTileService {
    private static final float MISSING = 32767;

    private final TileStrategy interpolated = new InterpolatedTiles();
    private final TileStrategy skipped = new SkippedTiles();

    public NormalInfo metadata(NormalData normal) {
        TileStrategy strategy = canSkip(normal) ? skipped : interpolated;
        int tileSize = strategy.tileSize(normal.res);
        int maxZoom = strategy.maxZoom(normal.res);
        NormalInfo info = new NormalInfo();
        info.res = normal.res;
        info.bounds = normal.bounds;
        info.tileSize = tileSize;
        info.maxZoom = maxZoom;
        info.maxRes = 360.0f / (2 << maxZoom) / tileSize;
        info.unit = normal.unit;
        return info;
    }

    public byte[] tile(NormalData normal, int z, int x, int y) throws IOException {
        TileStrategy strategy = canSkip(normal) ? skipped : interpolated;
        int tileSize = strategy.tileSize(normal.res);
        float tileCover = 360.0f / (2 << z);
        float tileRes = tileCover / tileSize;
        int tileCount = 2 << z;
        int tileX = x < 0 ? x + tileCount : x;
        float[][] values = new float[tileSize][tileSize];
        for (int row = 0; row < tileSize; row++) {
            for (int column = 0; column < tileSize; column++) {
                float latitude = (y * tileSize + row) * tileRes - 90.0f;
                float longitude = (tileX * tileSize + column) * tileRes - 180.0f;
                values[row][column] = strategy.cell(new LatLng(latitude, longitude), normal);
            }
        }
        return encode(values);
    }

    private boolean canSkip(NormalData normal) {
        return divisible(360, normal.res)
                && divisible(normal.bounds._southWest.lat, normal.res)
                && divisible(normal.bounds._southWest.lng, normal.res)
                && skipped.tileSize(normal.res) <= 256;
    }

    private static boolean divisible(float dividend, float divisor) {
        double left = Double.parseDouble(Float.toString(dividend));
        double right = Double.parseDouble(Float.toString(divisor));
        double ratio = left / right;
        return Math.round(ratio) * 1e6 == Math.round(ratio * 1e6);
    }

    private byte[] encode(float[][] values) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(values.length * values.length * 2);
        try (DataOutputStream data = new DataOutputStream(output)) {
            for (float[] row : values) {
                for (float value : row) {
                    data.writeShort(Float.isNaN(value) ? (int) MISSING : Math.round(value * 10));
                }
            }
        }
        return output.toByteArray();
    }

    private interface TileStrategy {
        int tileSize(float dataRes);

        int maxZoom(float dataRes);

        float cell(LatLng point, NormalData normal);
    }

    private static final class InterpolatedTiles implements TileStrategy {
        @Override
        public int tileSize(float dataRes) {
            return 256;
        }

        @Override
        public int maxZoom(float dataRes) {
            float resolution = 360.0f / 256;
            int maxZoom = -1;
            while (resolution > dataRes) {
                resolution /= 2;
                maxZoom++;
            }
            return maxZoom;
        }

        @Override
        public float cell(LatLng point, NormalData normal) {
            if (!contains(normal.bounds, point)) {
                return Float.NaN;
            }
            float x = (point.lng - normal.bounds._southWest.lng) / normal.res;
            float y = (point.lat - normal.bounds._southWest.lat) / normal.res;
            x = clamp(x, 0, normal.data[0].length - 1);
            y = clamp(y, 0, normal.data.length - 1);
            int x0 = (int) Math.floor(x);
            int x1 = (int) Math.ceil(x);
            int y0 = (int) Math.floor(y);
            int y1 = (int) Math.ceil(y);
            float px = ((point.lng - normal.bounds._southWest.lng) % normal.res) / normal.res;
            float py = ((point.lat - normal.bounds._southWest.lat) % normal.res) / normal.res;
            return (normal.data[y0][x0] * (1 - py) + normal.data[y1][x0] * py) * (1 - px)
                    + (normal.data[y0][x1] * (1 - py) + normal.data[y1][x1] * py) * px;
        }
    }

    private static final class SkippedTiles implements TileStrategy {
        @Override
        public int tileSize(float dataRes) {
            int tileSize = (int) (360 / dataRes);
            while (tileSize % 2 == 0) {
                tileSize /= 2;
                if (tileSize > 128 && tileSize <= 256) {
                    break;
                }
            }
            return tileSize;
        }

        @Override
        public int maxZoom(float dataRes) {
            int globalSize = (int) (360 / dataRes);
            return (int) (Math.log((double) globalSize / tileSize(dataRes)) / Math.log(2)) - 1;
        }

        @Override
        public float cell(LatLng point, NormalData normal) {
            if (!contains(normal.bounds, point)) {
                return Float.NaN;
            }
            float x = (point.lng - normal.bounds._southWest.lng) / normal.res;
            float y = (point.lat - normal.bounds._southWest.lat) / normal.res;
            return normal.data[Math.round(y)][Math.round(x)];
        }
    }

    private static boolean contains(LatLngBounds bounds, LatLng point) {
        return point.lat >= bounds._southWest.lat && point.lat <= bounds._northEast.lat
                && point.lng >= bounds._southWest.lng && point.lng <= bounds._northEast.lng;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
