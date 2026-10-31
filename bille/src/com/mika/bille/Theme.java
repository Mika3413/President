package com.mika.bille;

/** Palette de chaque monde. */
final class Theme {
    int bgTop, bgBottom, bokeh;
    int floorLight, floorDark, floorSpeck;
    int wallTopLight, wallTopDark, wallSide, wallEdge, wallSpeck;
    int frameLight, frameDark;
    int accent, accent2;
    int pitRim;
    boolean lava, cracks, frost;

    static Theme of(int world) {
        Theme t = new Theme();
        switch (world) {
            case 1: // Palais de Cristal
                t.bgTop = 0xFF0B1A3A; t.bgBottom = 0xFF02060F; t.bokeh = 0xFF5FB4FF;
                t.floorLight = 0xFF3A78B0; t.floorDark = 0xFF14345A; t.floorSpeck = 0xFFBFE6FF;
                t.wallTopLight = 0xFFF4FBFF; t.wallTopDark = 0xFF9CC4E8; t.wallSide = 0xFF4E74A0; t.wallEdge = 0xFF26446A; t.wallSpeck = 0xFFFFFFFF;
                t.frameLight = 0xFFD9E4EF; t.frameDark = 0xFF5D6F84;
                t.accent = 0xFF7FE8FF; t.accent2 = 0xFFB98CFF;
                t.pitRim = 0xFF9FD0FF;
                t.frost = true;
                break;
            case 2: // Cœur de Magma
                t.bgTop = 0xFF2A0A04; t.bgBottom = 0xFF070101; t.bokeh = 0xFFFF6A2A;
                t.floorLight = 0xFF3A302D; t.floorDark = 0xFF161111; t.floorSpeck = 0xFF6E5E58;
                t.wallTopLight = 0xFF5A4C48; t.wallTopDark = 0xFF2B2321; t.wallSide = 0xFF120D0C; t.wallEdge = 0xFFFF7A2E; t.wallSpeck = 0xFF8A7670;
                t.frameLight = 0xFF4A3530; t.frameDark = 0xFF1A100E;
                t.accent = 0xFFFF9A3D; t.accent2 = 0xFFFFD166;
                t.pitRim = 0xFFFF8A3D;
                t.lava = true; t.cracks = true;
                break;
            default: // Jardin de Jade
                t.bgTop = 0xFF0E2A22; t.bgBottom = 0xFF020B08; t.bokeh = 0xFF7DFFB8;
                t.floorLight = 0xFF2F7A62; t.floorDark = 0xFF103C30; t.floorSpeck = 0xFF9FE8C8;
                t.wallTopLight = 0xFFF7EEDB; t.wallTopDark = 0xFFC9B58D; t.wallSide = 0xFF6E5A3C; t.wallEdge = 0xFF3D2F1C; t.wallSpeck = 0xFF8C7650;
                t.frameLight = 0xFF8A5A32; t.frameDark = 0xFF3A2312;
                t.accent = 0xFFFFD15C; t.accent2 = 0xFF7DFFB8;
                t.pitRim = 0xFF6FCFA8;
                break;
        }
        return t;
    }
}
