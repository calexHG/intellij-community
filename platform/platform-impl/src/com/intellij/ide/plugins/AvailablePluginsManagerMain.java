/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * User: anna
 */
public class AvailablePluginsManagerMain extends PluginManagerMain {
  private PluginManagerMain installed;

  public AvailablePluginsManagerMain(PluginManagerMain installed, PluginManagerUISettings uiSettings) {
    super(uiSettings);
    this.installed = installed;
    init();
    final JButton manageRepositoriesBtn = new JButton("Manage repositories...");
    manageRepositoriesBtn.setMnemonic('m');
    manageRepositoriesBtn.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(myActionsPanel, new PluginHostsConfigurable())) {
          final ArrayList<String> pluginHosts = UpdateSettings.getInstance().myPluginHosts;
          if (!pluginHosts.contains(((AvailablePluginsTableModel)pluginsModel).getRepository())) {
            ((AvailablePluginsTableModel)pluginsModel).setRepository(AvailablePluginsTableModel.ALL, myFilter.getFilter().toLowerCase());
          }
          loadAvailablePlugins();
        }
      }
    });
    myActionsPanel.add(manageRepositoriesBtn);

    final JButton httpProxySettingsButton = new JButton(IdeBundle.message("button.http.proxy.settings"));
    httpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
        if (settingsDialog.isOK()) {
          loadAvailablePlugins();
        }
      }
    });
    myActionsPanel.add(httpProxySettingsButton, BorderLayout.NORTH);
  }

  @Override
  protected JScrollPane createTable() {
    pluginsModel = new AvailablePluginsTableModel();
    pluginTable = new PluginTable(pluginsModel);
    JScrollPane availableScrollPane = ScrollPaneFactory.createScrollPane(pluginTable);
    

    return availableScrollPane;
  }

  @Override
  public void reset() {
    super.reset();

    UiNotifyConnector.doWhenFirstShown(getPluginTable(), new Runnable() {
      @Override
      public void run() {
        loadAvailablePlugins();
      }
    });
  }

  @Override
  protected ActionGroup getActionGroup(boolean inToolbar) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (!inToolbar) {
      actionGroup.add(new ActionInstallPlugin(this, installed));
    }
    actionGroup.add(new RefreshAction());
    if (inToolbar) {
      actionGroup.add(new MyFilterRepositoryAction());
      actionGroup.add(new MyFilterCategoryAction());
      actionGroup.add(new SortByNameAction());
    }
    return actionGroup;
  }

  @Override
  protected void propagateUpdates(ArrayList<IdeaPluginDescriptor> list) {
    installed.modifyPluginsList(list); //propagate updates
  }

  private class MyFilterCategoryAction extends ComboBoxAction implements DumbAware{
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Category: " + ((AvailablePluginsTableModel)pluginsModel).getCategory());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final LinkedHashSet<String> availableCategories = ((AvailablePluginsTableModel)pluginsModel).getAvailableCategories();
      final DefaultActionGroup gr = new DefaultActionGroup();
      gr.add(createFilterByCategoryAction(AvailablePluginsTableModel.ALL));
      for (final String availableCategory : availableCategories) {
        gr.add(createFilterByCategoryAction(availableCategory));
      }
      return gr;
    }

    private AnAction createFilterByCategoryAction(final String availableCategory) {
      return new AnAction(availableCategory) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase();
          ((AvailablePluginsTableModel)pluginsModel).setCategory(availableCategory, filter);
        }
      };
    }
  }

  private class MyFilterRepositoryAction extends ComboBoxAction implements DumbAware {

    private static final int LENGTH = 15;

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(!UpdateSettings.getInstance().myPluginHosts.isEmpty());
      String repository = ((AvailablePluginsTableModel)pluginsModel).getRepository();
      if (repository.length() > LENGTH) {
        repository = repository.substring(0, LENGTH) + "...";
      }
      e.getPresentation().setText("Repository: " + repository);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      gr.add(createFilterByRepositoryAction(AvailablePluginsTableModel.ALL));
      gr.add(createFilterByRepositoryAction(AvailablePluginsTableModel.JETBRAINS_REPO));
      for (final String host : UpdateSettings.getInstance().myPluginHosts) {
        gr.add(createFilterByRepositoryAction(host));
      }
      return gr;
    }

    private AnAction createFilterByRepositoryAction(final String host) {
      return new AnAction(host) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase();
          ((AvailablePluginsTableModel)pluginsModel).setRepository(host, filter);
        }
      };
    }
  }

  private class SortByNameAction extends ComboBoxAction implements DumbAware{

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Sort by: " + pluginsModel.getSortMode());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      for (final String sortMode : AvailablePluginsTableModel.SORT_MODES) {
        gr.add(createSortByAction(sortMode));
      }
      if (!UpdateSettings.getInstance().myPluginHosts.isEmpty()) {
        gr.add(createSortByAction(AvailablePluginsTableModel.REPOSITORY));
      }
      return gr;
    }

    private AnAction createSortByAction(final String sortMode) {
      return new AnAction(sortMode) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          pluginsModel.setSortMode(sortMode);
        }
      };
    }
  }
}
