package com.gravity.map;

import java.util.List;

import org.newdawn.slick.geom.Shape;
import org.newdawn.slick.geom.Vector2f;

import com.gravity.physics.Collision;
import com.gravity.physics.Entity;

/**
 * Represents a convex shape in map terrain for collision detection.
 * 
 * @author xiao
 */
public class TileWorldEntity implements Entity {

    private Shape position;
    @SuppressWarnings("unused")
    private TileWorld map;

    public TileWorldEntity(Shape shape, TileWorld map) {
        this.position = shape;
        this.map = map;
    }

    @Override
    public Vector2f getPosition(int ticks) {
        return null;
    }

    @Override
    public Vector2f getVelocity(int ticks) {
        return new Vector2f(0, 0);
    }

    @Override
    public Shape handleCollisions(int ticks, List<Collision> collisions) {
        return position;
    }

    @Override
    public Shape rehandleCollisions(int ticks, List<Collision> collisions) {
        return position;
    }

    @Override
    public void tick(int ticks) {
        // Map Entity does not change over time
    }

    @Override
    public Shape getShape(int ticks) {
        return position;
    }

}
