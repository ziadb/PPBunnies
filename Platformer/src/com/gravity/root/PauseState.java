package com.gravity.root;

import java.util.List;

import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Vector2f;
import org.newdawn.slick.state.transition.FadeInTransition;
import org.newdawn.slick.state.transition.FadeOutTransition;

import com.google.common.collect.Lists;
import com.gravity.geom.Rect.Side;
import com.gravity.levels.CageRenderer;
import com.gravity.levels.GameplayState;
import com.gravity.levels.LevelInfo;
import com.gravity.levels.MenuCage;
import com.gravity.levels.Renderer;
import com.gravity.levels.ResumeCage;

public class PauseState extends CageSelectState {

    public static final int ID = 22;

    private ResumeCage resumeCage;

    public PauseState() throws SlickException {
        super(new LevelInfo("Pause", "new-assets/levels/pause.tmx", ID));
    }

    @Override
    public void keyPressed(int key, char c) {
        if (key == Input.KEY_ESCAPE) {
            enterCageState(resumeCage.getToState());
        } else {
            super.keyPressed(key, c);
        }
    }

    @Override
    public void enterCageState(int state) {
        if (state == resumeCage.getToState()) {
            game.enterState(state, new SlideTransition(game.getState(state), Side.TOP, 1000), null);
        } else {
            // Main menu
            GameplayState fromState = (GameplayState) game.getState(state);
            fromState.reset();
            try {
                fromState.init(container, game);
            } catch (SlickException e) {
                e.printStackTrace();
            }
            game.enterState(state, new FadeOutTransition(), new FadeInTransition());
        }
    }

    @Override
    protected CagesAndRenderers constructCagesAndRenderers() {
        List<MenuCage> cages = Lists.newLinkedList();
        List<Renderer> renderers = Lists.newLinkedList();

        Vector2f resumeLoc = map.getSpecialLocation("resume");
        Vector2f mainMenuLoc = map.getSpecialLocation("mainmenu");

        resumeCage = new ResumeCage(game, resumeLoc.x, resumeLoc.y, MainMenuState.ID);
        MenuCage mainMenuCage = new MenuCage(game, mainMenuLoc.x, mainMenuLoc.y, MainMenuState.ID);

        CageRenderer resumeRend, mainMenuRend;
        try {
            resumeRend = new CageRenderer(resumeCage, "Resume");
            mainMenuRend = new CageRenderer(mainMenuCage, "Main Menu");
        } catch (SlickException e) {
            throw new RuntimeException(e);
        }

        renderers.add(resumeRend);
        renderers.add(mainMenuRend);
        cages.add(resumeCage);
        cages.add(mainMenuCage);

        return new CagesAndRenderers(cages, renderers);
    }

    public void setGameplayState(GameplayState gameplayState) {
        resumeCage.setToState(gameplayState.getID());
    }

}
