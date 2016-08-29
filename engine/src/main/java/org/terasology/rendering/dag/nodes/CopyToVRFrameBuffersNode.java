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

import openvrprovider.OpenVRProvider;
import openvrprovider.OpenVRStereoRenderer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.terasology.assets.ResourceUrn;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.config.RenderingDebugConfig;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.registry.In;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.dag.AbstractNode;
import org.terasology.rendering.oculusVr.OculusVrHelper;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.FINAL;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.READ_ONLY_GBUFFER;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import static org.terasology.rendering.opengl.ScalingFactors.FULL_SCALE;
import org.terasology.rendering.opengl.ScreenGrabber;
import org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs;
import org.terasology.rendering.world.WorldRenderer;
import org.terasology.rendering.world.WorldRenderer.RenderingStage;
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
public class CopyToVRFrameBuffersNode extends AbstractNode {
    public static final ResourceUrn OC_UNDISTORTED = new ResourceUrn("engine:ocUndistorted");
    private OpenVRProvider vrProvider = null;
    private OpenVRStereoRenderer vrRenderer = null;

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
    private Material ocDistortion;

    private FBO ocUndistorted;

    public void setOpenVRProvider(OpenVRProvider providerToSet)
    {
        this.vrProvider = providerToSet;
    }


    @Override
    public void initialise() {
        renderingConfig = config.getRendering();
        renderingDebugConfig = renderingConfig.getDebug();

        finalPost = worldRenderer.getMaterial("engine:prog.post"); // TODO: rename shader to finalPost
        debug = worldRenderer.getMaterial("engine:prog.debug");
        // TODO: rethink debug strategy in light of the DAG-based architecture
        requiresFBO(new FBOConfig(OC_UNDISTORTED, FULL_SCALE, FBO.Type.DEFAULT), displayResolutionDependentFBOs);
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
     * worldRenderer.getCurrentRenderStage() Can be MONO, LEFT_EYE or RIGHT_EYE, and communicates to the method weather
     * it is dealing with a standard display or an OculusVR setup, and in the
     * latter case, which eye is currently being rendered. Notice that if the
     * OculusVR support is enabled, the image is sent to screen or saved to
     * file only when the value passed in is RIGHT_EYE, as the processing for
     * the LEFT_EYE comes first and leads to an incomplete image.
     */
    @Override
    public void process() {
        PerformanceMonitor.startActivity("rendering/finalPostProcessing");

        ocUndistorted = displayResolutionDependentFBOs.get(OC_UNDISTORTED);

        fullScale = READ_ONLY_GBUFFER.dimensions();

        if (!renderingDebugConfig.isEnabled()) {
            finalPost.enable();
        } else {
            debug.enable();
        }

        if (!renderingConfig.isOculusVrSupport()) {
            renderFinalMonoImage();
        } else {
            renderFinalStereoImage(worldRenderer.getCurrentRenderStage());
        }

        PerformanceMonitor.endActivity();
    }

    private void renderFinalMonoImage() {
        if (screenGrabber.isNotTakingScreenshot()) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary
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

    // TODO: have a flag to invert the eyes (Cross Eye 3D), as mentioned in
    // TODO: http://forum.terasology.org/threads/happy-coding.1018/#post-11264
    private void renderFinalStereoImage(RenderingStage renderingStage) {
        if (this.vrRenderer == null)
            this.vrRenderer = new OpenVRStereoRenderer(this.vrProvider, 1280, 720);
        switch (renderingStage) {
            case LEFT_EYE:
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary
                vrProvider.updatePose();
                org.lwjgl.opengl.EXTFramebufferObject.glBindFramebufferEXT(
                        org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_EXT, vrRenderer.getTextureHandleForEyeFramebuffer(0));
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary
                renderFullscreenQuad(0, 0, 1280, 720);

                break;

            case RIGHT_EYE:
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary
                org.lwjgl.opengl.EXTFramebufferObject.glBindFramebufferEXT(
                        org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_EXT, vrRenderer.getTextureHandleForEyeFramebuffer(1));
                renderFullscreenQuad(0, 0, 1280, 720);
                vrProvider.submitFrame();
                GL11.glFinish();
                break;
            case MONO:
                break;
        }
    }
}
