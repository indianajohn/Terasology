/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.dag.nodes;

import org.lwjgl.opengl.Display;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.config.RenderingDebugConfig;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.registry.In;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.dag.AbstractNode;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.FINAL;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.READ_ONLY_GBUFFER;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.ScreenGrabber;
import org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs;
import org.terasology.rendering.world.WorldRenderer;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.terasology.rendering.opengl.OpenGLUtils.bindDisplay;
import static org.terasology.rendering.opengl.OpenGLUtils.renderFullscreenQuad;
import static org.terasology.rendering.opengl.OpenGLUtils.setViewportToSizeOf;

/**
 * TODO: Add diagram of this node
 * TODO: Break into two different nodes
 * TODO: Add desired state changes after breaking into different nodes
 */
public class FinalPostProcessingNode extends AbstractNode {

    @In
    private WorldRenderer worldRenderer;

    @In
    private Config config;

    @In
    private ScreenGrabber screenGrabber;

    @In
    private DisplayResolutionDependentFBOs displayResolutionDependentFBOs;

    private RenderingDebugConfig renderingDebugConfig;
    private RenderingConfig renderingConfig;

    private FBO.Dimensions fullScale;

    private Material finalPost;
    private Material debug;

    @Override
    public void initialise() {
        renderingConfig = config.getRendering();
        renderingDebugConfig = renderingConfig.getDebug();

        finalPost = worldRenderer.getMaterial("engine:prog.post"); // TODO: rename shader to finalPost
        debug = worldRenderer.getMaterial("engine:prog.debug");
    }

    /**
     * If each is enabled through the rendering settings, this method
     * adds depth-of-field blur, motion blur and film grain to the rendering
     * obtained so far. If OculusVR support is enabled, it composes (over two
     * calls) the images for each eye into a single image, and applies a distortion
     * pattern to each, to match the optics in the OculusVR headset.
     * <p>
     * Finally, it either sends the image to the display or, when taking a screenshot,
     * instructs the FrameBuffersManager to save it to a file.
     * <p>
     */
    @Override
    public void process() {
        PerformanceMonitor.startActivity("rendering/finalPostProcessing");

        fullScale = READ_ONLY_GBUFFER.dimensions();

        if (!renderingDebugConfig.isEnabled()) {
            finalPost.enable();
        } else {
            debug.enable();
        }

        renderFinalMonoImage();

        PerformanceMonitor.endActivity();
    }

    private void renderFinalMonoImage() {
        if (screenGrabber.isNotTakingScreenshot()) {
            bindDisplay();
            renderFullscreenQuad(0, 0, Display.getWidth(), Display.getHeight());

        } else {
            FINAL.bind();


            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary
            renderFullscreenQuad(0, 0, fullScale.width(), fullScale.height());

            screenGrabber.saveScreenshot();
            // when saving a screenshot we do not send the image to screen,
            // to avoid the brief one-frame flicker of the screenshot

            // This is needed to avoid the UI (which is not currently saved within the
            // screenshot) being rendered for one frame with buffers.sceneFinal size.
            setViewportToSizeOf(READ_ONLY_GBUFFER);
        }
    }
}
