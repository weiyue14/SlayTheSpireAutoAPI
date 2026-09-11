package com.megacrit.cardcrawl.map;

import java.util.ArrayList;

public class MapRoomNode {
    public int x;
    public int y;

    // offline smoke-test hook (never packaged into the mod jar)
    public ArrayList<MapEdge> edges = new ArrayList<>();

    public boolean hasEdges() {
        return !edges.isEmpty();
    }

    public ArrayList<MapEdge> getEdges() {
        return edges;
    }

    public String getRoomSymbol(boolean forMap) {
        return "?";
    }
}
