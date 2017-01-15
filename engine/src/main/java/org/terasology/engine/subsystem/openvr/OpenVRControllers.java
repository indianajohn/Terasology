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
package org.terasology.engine.subsystem.openvr;

import jopenvr.VRControllerState_t;
import org.joml.Matrix4f;
import org.terasology.input.ButtonState;
import org.terasology.input.ControllerDevice;
import org.terasology.input.InputType;
import org.terasology.input.device.ControllerAction;
import org.terasology.rendering.openvrprovider.ControllerListener;
import org.terasology.rendering.openvrprovider.OpenVRUtil;
import org.terasology.input.ControllerId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class OpenVRControllers implements ControllerDevice, ControllerListener {

    Queue<ControllerAction> queuedActions = new ArrayDeque<>();

    @Override
    public List<String> getControllers() {
        List<String> ids = new ArrayList<>();
        ids.add("OpenVR");
        return ids;
    }

    @Override
    public Queue<ControllerAction> getInputQueue() {
        Queue<ControllerAction> result = new ArrayDeque<>();
        result.addAll(queuedActions);
        queuedActions.clear();
        return result;
    }

    @Override
    public void buttonStateChanged(VRControllerState_t stateBefore, VRControllerState_t stateAfter, int nController) {
        if (nController != 0)
            return;
        if (OpenVRUtil.switchedUp(ControllerListener.BUTTON_TRIGGER, stateBefore.ulButtonPressed, stateAfter.ulButtonPressed)) {
            queuedActions.add(new ControllerAction(InputType.CONTROLLER_BUTTON.getInput(ControllerId.ZERO),
                    "OpenVR", ButtonState.UP, 1.0f));
        }
        else if (OpenVRUtil.switchedDown(ControllerListener.BUTTON_TRIGGER, stateBefore.ulButtonPressed, stateAfter.ulButtonPressed)) {
            queuedActions.add(new ControllerAction(InputType.CONTROLLER_BUTTON.getInput(ControllerId.ZERO),
                    "OpenVR", ButtonState.DOWN, 1.0f));
        }
        // TODO: axes, other buttons
    }

    @Override
    public void poseChanged(Matrix4f pose, int handIndex) {
        // currently no actions are sensitive to controller movement
    }

}
