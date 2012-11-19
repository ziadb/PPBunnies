package com.gravity.map;

import java.util.List;
import java.util.Map;

import org.newdawn.slick.Graphics;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Vector2f;
import org.newdawn.slick.tiled.GroupObject;
import org.newdawn.slick.tiled.Layer;
import org.newdawn.slick.tiled.ObjectGroup;
import org.newdawn.slick.tiled.Tile;
import org.newdawn.slick.tiled.TileSet;
import org.newdawn.slick.tiled.TiledMapPlus;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.gravity.entity.TriggeredText;
import com.gravity.entity.TriggeredTextEntity;
import com.gravity.geom.Rect;
import com.gravity.physics.Collidable;
import com.gravity.physics.CollisionEngine;
import com.gravity.root.GameplayControl;

public class TileWorld implements GameWorld {
    public final int height;
    public final int width;

    public final int tileHeight;
    public final int tileWidth;

    private List<Collidable> entityNoCalls, entityCallColls;
    private List<TriggeredText> triggeredTexts;
    private Map<Layer, List<MovingCollidable>> movingCollMap;

    private List<Vector2f> startPositions = null;
    private List<DisappearingTileController> disappearingTileControllers;

    private final String name;
    public final TiledMapPlus map;
    private final GameplayControl controller;

    private interface CollidableCreator<T extends Collidable> {
        T createCollidable(Rect r);
    }

    public TileWorld(String name, TiledMapPlus map, GameplayControl controller) {
        this.map = map;
        this.controller = controller;
        this.name = name;

        // Get width/height
        this.tileWidth = map.getTileWidth();
        this.tileHeight = map.getTileHeight();
        this.width = map.getWidth() * tileWidth;
        this.height = map.getHeight() * tileHeight;
    }

    /**
     * Process a layer of the map for collisions. Merge adjacent tiles vertically, then horizontally.
     * 
     * @param layerName
     *            the name of the layer in the map to process.
     * @param creator
     *            a Creator to create collidables for
     * @return a list of collidables in this layer. Returns an empty list if the list does not exist.
     */
    private <T extends Collidable> List<T> processLayer(String layerName, CollidableCreator<T> creator) {
        boolean[][] visited = new boolean[map.getWidth()][map.getHeight()];
        List<T> res = Lists.newArrayList();
        int layer = 0;
        try {
            layer = map.getLayerID(layerName);
        } catch (NullPointerException e) {
            System.err.println("WARNING: Layer " + layerName + " not found, returning empty collidables list.");
            return res;
        }

        int first, i, j, tileId;
        for (i = 0; i < map.getWidth(); i++) {
            first = 0;
            while (first < map.getHeight()) {
                tileId = map.getTileId(i, first, layer);
                visited[i][first] = true;
                if (tileId != 0) {
                    j = first + 1;
                    while (j < map.getHeight() && map.getTileId(i, j, layer) != 0) {
                        visited[i][j] = true;
                        j++;
                    }
                    Rect r = new Rect(i * tileWidth, first * tileHeight, tileWidth, tileHeight * (j - first));
                    res.add(creator.createCollidable(r));
                    first = j;
                } else {
                    first++;
                }
            }
        }

        for (j = 0; j < map.getHeight(); j++) {
            first = 0;
            while (first < map.getWidth()) {
                tileId = visited[first][j] ? 0 : map.getTileId(first, j, layer);
                if (tileId != 0) {
                    i = first + 1;
                    while (i < map.getWidth() && map.getTileId(i, j, layer) != 0) {
                        visited[i][j] = true;
                        i++;
                    }
                    Rect r = new Rect(first * tileWidth, j * tileHeight, tileWidth * (i - first), tileHeight);
                    res.add(creator.createCollidable(r));
                    first = i;
                } else {
                    first++;
                }
            }
        }
        return res;
    }

    @Override
    public void initialize() {
        // Iterate over and find all tiles
        Layer terrain = map.getLayer("map");
        if (terrain != null) {

        } else {
            entityNoCalls = processLayer(TILES_LAYER_NAME, new CollidableCreator<Collidable>() {
                @Override
                public Collidable createCollidable(Rect r) {
                    return new StaticCollidable(r);
                }
            });

            entityCallColls = processLayer(SPIKES_LAYER_NAME, new CollidableCreator<Collidable>() {
                @Override
                public Collidable createCollidable(Rect r) {
                    return new SpikeEntity(controller, r);
                }
            });

            entityNoCalls.addAll(processLayer(BOUNCYS_LAYER_NAME, new CollidableCreator<Collidable>() {
                @Override
                public Collidable createCollidable(Rect r) {
                    return new BouncyTile(r);
                }
            }));

        }

        triggeredTexts = Lists.newArrayList();
        for (Layer layer : map.getLayers()) {
            int x = Integer.parseInt(layer.props.getProperty("x", "-1"));
            int y = Integer.parseInt(layer.props.getProperty("y", "-1"));
            String text = layer.props.getProperty("text", null);
            if (x < 0 || y < 0 || text == null) {
                continue;
            }

            // if text layer is found, make layer invisible
            layer.visible = false;
            TriggeredText triggeredText;
            triggeredText = new TriggeredText(x, y, text);
            System.out.println("found text layer: " + text);
            triggeredTexts.add(triggeredText);
            try {
                for (Tile tile : layer.getTiles()) {
                    Rect r = new Rect(tile.x * tileWidth, tile.y * tileHeight, tileWidth, tileHeight);
                    TriggeredTextEntity tte = new TriggeredTextEntity(r, triggeredText);
                    entityCallColls.add(tte);
                }
            } catch (SlickException e) {
                throw new RuntimeException("Unable to get tiles for map layer " + layer.name, e);
            }
        }

        movingCollMap = Maps.newHashMap();
        for (Layer layer : map.getLayers()) {
            final float speed = Float.parseFloat(layer.props.getProperty("speed", "-1.0"));
            final int transX = Integer.parseInt(layer.props.getProperty("translationX", "-22222"));
            final int transY = Integer.parseInt(layer.props.getProperty("translationY", "-22222"));

            if (speed < 0 || transX == -22222 || transY == -22222)
                continue;

            // Found a moving layer.
            layer.visible = false;
            List<Collidable> colls = processLayer(layer.name, new CollidableCreator<Collidable>() {
                @Override
                public Collidable createCollidable(Rect r) {
                    return new MovingCollidable(controller, tileWidth, tileHeight, r, transX, transY, speed);
                }
            });
            entityNoCalls.addAll(colls);

            List<MovingCollidable> movingColls = Lists.newArrayList();
            for (Collidable c : colls) {
                movingColls.add((MovingCollidable) c);
            }
            movingCollMap.put(layer, movingColls);
        }

        for (Layer layer : map.getLayers()) {
            String type = layer.props.getProperty("type", "");
            if (!type.equals("checkpoint"))
                continue;

            layer.visible = false;
            try {
                // System.out.println("checkpoint layer " + layer.name + " !");
                Vector2f startPosA = null, startPosB = null;
                for (Tile tile : layer.getTiles()) {
                    int tileID = layer.getTileID(tile.x, tile.y);
                    TileSet tileSet = map.getTileSetByGID(tileID);
                    int startIDA = tileSet.getGlobalIDByPosition(3, 1);
                    int startIDB = tileSet.getGlobalIDByPosition(3, 2);
                    if (tileID == startIDA) {
                        // Pink start
                        startPosA = new Vector2f(tile.x * tileWidth, tile.y * tileHeight);
                    } else if (tileID == startIDB) { // !!!!
                        // Yellow start
                        startPosB = new Vector2f(tile.x * tileWidth, tile.y * tileHeight);
                    }
                }
                if (startPosA == null || startPosB == null) {
                    System.err.println("WARNING: skipping checkpoint layer " + layer.name);
                    continue;
                }

                List<Vector2f> newStartPositions = Lists.newArrayList(startPosA, startPosB);
                Checkpoint checkpoint = new Checkpoint(controller, newStartPositions);
                for (Tile tile : layer.getTiles()) {
                    int tileID = layer.getTileID(tile.x, tile.y);
                    TileSet tileSet = map.getTileSetByGID(tileID);
                    int checkID = tileSet.getGlobalIDByPosition(3, 0);
                    if (layer.getTileID(tile.x, tile.y) == checkID) {
                        Rect r = new Rect(tile.x * tileWidth, tile.y * tileHeight, tileWidth, tileHeight);
                        entityCallColls.add(new CheckpointCollidable(checkpoint, r));
                    }
                }
            } catch (SlickException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public List<Collidable> getTerrainEntitiesNoCalls() {
        return entityNoCalls;
    }

    @Override
    public Layer getLayer(String name) {
        return map.getLayer(name);
    }

    @Override
    public void render(Graphics g, int offsetX, int offsetY) {
        //@formatter:off
        /*
         * // if we need to draw hitboxes again:

        g.pushTransform();
        g.translate(offsetX, offsetY);
        g.setColor(Color.red);
        for (Collidable e : entityNoCalls) {
            g.draw(e.getRect(0).toShape());
        }
        g.setColor(Color.white);
        g.resetTransform();
        g.popTransform();
        */ 
        //@formatter:on

        map.render(offsetX, offsetY);
    }

    @Override
    public List<Collidable> getTerrainEntitiesCallColls() {
        return entityCallColls;
    }

    @Override
    public List<Vector2f> getPlayerStartPositions() {
        if (startPositions != null) {
            return startPositions;
        }

        Layer layer = map.getLayer(PLAYERS_LAYER_NAME);
        if (layer == null) {
            System.err.println("WARNING: Map \"" + name + "\" doesn't contain player start positions, using default positions instead.");
            return Lists.newArrayList(PLAYER_ONE_DEFAULT_STARTPOS, PLAYER_TWO_DEFAULT_STARTPOS);
        }
        layer.visible = false;
        try {
            List<Vector2f> res = Lists.newArrayList();
            for (Tile tile : layer.getTiles()) {
                res.add(new Vector2f(tile.x * tileWidth, tile.y * tileHeight));
            }
            Preconditions.checkArgument(res.size() == 2, "Wrong number of player start positions in map \"" + name + "\", expected 2 but found "
                    + res.size());
            return res;
        } catch (SlickException e) {
            System.err.println(e);
            return Lists.newArrayList(PLAYER_ONE_DEFAULT_STARTPOS, PLAYER_TWO_DEFAULT_STARTPOS);
        }
    }

    @Override
    public Rect getFinishRect() {
        GroupObject object;
        try {
            ObjectGroup group = map.getObjectGroup(MARKERS_LAYER_NAME);
            object = group.getObject(FINISH_MARKER_NAME);
            return new Rect(object.x, object.y, object.width, object.height);
        } catch (NullPointerException e) {
            System.err.println("No marker layer found for map " + map + " using right edge of map instead");
            return new Rect((map.getWidth() - 1) * tileWidth, 0, tileWidth, getHeight());
        }
    }

    @Override
    public String toString() {
        return "TileWorld [height=" + height + ", width=" + width + ", name=" + name + ", map=" + map + "]";
    }

    public List<TriggeredText> getTriggeredTexts() {
        return triggeredTexts;
    }

    /** Returns a list of DisappearingTileController instances if any disappearing tile layers are found. */
    @Override
    public List<DisappearingTileController> reinitializeDisappearingLayers(final CollisionEngine engine) {
        disappearingTileControllers = Lists.newArrayList();
        for (Layer l : map.getLayers()) {
            if (l.props.getProperty("type", "").equals(DISAPPEARING_LAYER_TYPE)) {
                final DisappearingTileController controller = createDisappearingTileController(l);
                List<DisappearingTile> coll = processLayer(l.name, new CollidableCreator<DisappearingTile>() {
                    @Override
                    public DisappearingTile createCollidable(Rect r) {
                        return new DisappearingTile(r, controller, engine);
                    }
                });
                entityNoCalls.addAll(coll);
                disappearingTileControllers.add(controller);
                for (DisappearingTile c : coll) {
                    controller.register(c);
                }
            }
        }
        return disappearingTileControllers;
    }

    private DisappearingTileController createDisappearingTileController(Layer l) {
        float invisibleTime = Float.parseFloat(l.props.getProperty(INVISIBLE_TIME_PROPERTY));
        float normalVisibleTime = Float.parseFloat(l.props.getProperty(NORMAL_VISIBLE_TIME_PROPERTY));
        float flickerTime = Float.parseFloat(l.props.getProperty(FLICKER_TIME_PROPERTY));
        float geometricParameter = Float.parseFloat(l.props.getProperty(GEOMETRIC_PARAMETER_PROPERTY));
        int flickerCount = Integer.parseInt(l.props.getProperty(FLICKER_COUNT_PROPERTY));

        return new DisappearingTileController(invisibleTime, normalVisibleTime, flickerTime, geometricParameter, flickerCount, l);
    }

    public Map<Layer, List<MovingCollidable>> getMovingCollMap() {
        return movingCollMap;
    }

    public void setStartPositions(List<Vector2f> startPositions) {
        this.startPositions = startPositions;
    }

}
