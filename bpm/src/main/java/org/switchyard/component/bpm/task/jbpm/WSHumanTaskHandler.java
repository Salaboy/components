/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.switchyard.component.bpm.task.jbpm;

import org.switchyard.component.bpm.task.Task;
import org.switchyard.component.bpm.task.TaskManager;
import org.switchyard.component.bpm.task.drools.DroolsTaskHandler;

/**
 * Wraps a jBPM {@link org.jbpm.process.workitem.wsht.WSHumanTaskHandler WSHumanTaskHandler}.
 *
 * @author David Ward &lt;<a href="mailto:dward@jboss.org">dward@jboss.org</a>&gt; (C) 2011 Red Hat Inc.
 */
public class WSHumanTaskHandler extends DroolsTaskHandler {

    /**
     * The default name for this TaskHandler.
     */
    public static final String HUMAN_TASK = "Human Task";

    private org.jbpm.process.workitem.wsht.WSHumanTaskHandler _wshth;

    /**
     * Constructs a new WSHumanTaskHandler with the default name.
     */
    public WSHumanTaskHandler() {
        super(HUMAN_TASK);
    }

    /**
     * Constructs a new WSHumanTaskHandler with the specified name.
     * @param name the specified name
     */
    public WSHumanTaskHandler(String name) {
        super(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeTask(Task task, TaskManager taskManager) {
        connect();
        super.executeTask(task, taskManager);
        taskManager.completeTask(task.getId(), task.getResults());
        disconnect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abortTask(Task task, TaskManager taskManager) {
        connect();
        super.abortTask(task, taskManager);
        taskManager.abortTask(task.getId());
        disconnect();
    }

    private void connect() {
        _wshth = new org.jbpm.process.workitem.wsht.WSHumanTaskHandler() {
            @Override
            public void connect() {
                boolean ready = false;
                int attempts = 0;
                while (!ready) {
                    try {
                        setClient(null);
                        super.connect();
                        ready = true;
                    } catch (Throwable t) {
                        try {
                            dispose();
                            Thread.sleep(1000);
                        } catch (Throwable ignore) {
                            // here to keep checkstyle happy ("Must have at least one statement.")
                            ignore.getMessage();
                        }
                        attempts++;
                        ready = attempts > 9;
                    }
                }
            }
        };
        setWorkItemHandler(_wshth);
    }

    private void disconnect() {
        if (_wshth != null) {
            try {
                _wshth.dispose();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                _wshth = null;
                setWorkItemHandler(null);
            }
        }
    }

}
