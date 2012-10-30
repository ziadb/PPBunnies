package com.gravity.entity;

import com.gravity.physics.Collidable;

/**
 * Represents a collidable object which moves in the world.
 * 
 * @author xiao
 */
public interface Entity extends UpdateCycling, PhysicallyStateful, Collidable {
}
