/*
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.fixedmode;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetPositioned;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Fixed Mode",
	description = "Resize the game while in fixed mode",
	tags = {"resize"}
)
public class FixedModePlugin extends Plugin
{
	private static final int DEFAULT_VIEW_HEIGHT = 334;
	private static final int EXPANDED_VIEW_HEIGHT = 476;
	private static final Set<WidgetInfo> AUTO_EXPAND_WIDGETS = ImmutableSet
		.<WidgetInfo>builder()
		.add(WidgetInfo.CHATBOX_MESSAGES_POPUP)
		.add(WidgetInfo.CHATBOX_MESSAGES_MAKEALL)
		.build();

	private static final Set<WidgetInfo> TO_CONTRACT_WIDGETS = ImmutableSet
		.<WidgetInfo>builder()
		.add(WidgetInfo.FIXED_VIEWPORT_BANK_POPUP_CONTAINER)
		.build();

	@Inject
	private Client client;

	@Inject
	private FixedModeConfig config;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private TranslateMouseListener mouseListener;

	@Inject
	private TranslateMouseWheelListener mouseWheelListener;

	private int lastMenu = 0;
	private boolean hideChat = true;

	@Provides
	FixedModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FixedModeConfig.class);
	}

	@Override
	protected void startUp()
	{
		updateConfig();
	}

	@Override
	protected void shutDown() throws Exception
	{
		// Unregister mouse translation listeners
		unregisterListeners();

		// Disable stretched fixed mode
		client.setStretchedEnabled(false);

		// Reset menu state
		hideChat = true;
		lastMenu = 0;

		// Reset widgets
		resetWidgets();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("fixedmode"))
		{
			return;
		}

		updateConfig();
	}

	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event)
	{
		if (!"Switch tab".equals(event.getMenuOption()))
		{
			return;
		}

		final Widget chatboxMessages = client.getWidget(WidgetInfo.CHATBOX_MESSAGES);
		final int newMenu = event.getWidgetId();
		hideChat = true;

		if (newMenu != lastMenu || (chatboxMessages != null && chatboxMessages.isHidden()))
		{
			hideChat = false;
			lastMenu = newMenu;
		}
	}

	@Subscribe
	public void onWidgetPositioned(final WidgetPositioned event)
	{
		if (!config.expandViewToChat() || client.isResized())
		{
			return;
		}

		if (hideChat)
		{
			// Expand the view height
			setViewSizeTo(DEFAULT_VIEW_HEIGHT, EXPANDED_VIEW_HEIGHT);
		}

		final Widget chatboxMessages = client.getWidget(WidgetInfo.CHATBOX_MESSAGES);

		if (chatboxMessages != null)
		{
			boolean found = !hideChat;

			// Check if any auto-expand interface is open
			if (!found)
			{
				for (final WidgetInfo widgetInfo : AUTO_EXPAND_WIDGETS)
				{
					final Widget widget = client.getWidget(widgetInfo);

					if (widget != null && !widget.isSelfHidden())
					{
						found = true;
						break;
					}
				}
			}

			// Resize some widgets that might interfere with having expanded chat
			setWidgetsSizeTo(
				found ? EXPANDED_VIEW_HEIGHT : DEFAULT_VIEW_HEIGHT,
				found ? DEFAULT_VIEW_HEIGHT : EXPANDED_VIEW_HEIGHT);

			// Hide/show chat messages
			chatboxMessages.setHidden(!found);
		}
	}

	private void setWidgetsSizeTo(final int originalHeight, final int newHeight)
	{
		final Widget viewport = client.getViewportWidget();

		if (viewport != null)
		{
			viewport.setHeight(newHeight);
		}

		for (final WidgetInfo widgetInfo : TO_CONTRACT_WIDGETS)
		{
			final Widget widget = client.getWidget(widgetInfo);

			if (widget != null && !widget.isSelfHidden())
			{
				changeWidgetHeight(originalHeight, newHeight, widget);
			}
		}
	}

	private void setViewSizeTo(final int originalHeight, final int newHeight)
	{
		final Widget fixedMain = client.getWidget(WidgetInfo.FIXED_MAIN);

		if (fixedMain != null && fixedMain.getHeight() == originalHeight)
		{
			fixedMain.setHeight(newHeight);

			final Widget[] staticChildren = fixedMain.getStaticChildren();

			// Expand all children of the main fixed view
			for (final Widget child : staticChildren)
			{
				changeWidgetHeight(originalHeight, newHeight, child);
			}
		}

	}

	private static void changeWidgetHeight(int originalHeight, int newHeight, Widget child)
	{
		if (child.getHeight() == originalHeight)
		{
			child.setHeight(newHeight);

			final Widget[] nestedChildren = child.getNestedChildren();

			// Expand nested children (e.g basically full-view overlays, so God Wars
			// and the new raids 2 city)
			if (nestedChildren != null)
			{
				for (final Widget nestedChild : nestedChildren)
				{
					if (nestedChild.getHeight() == originalHeight)
					{
						nestedChild.setHeight(newHeight);
					}
				}
			}
		}
	}

	private void resetWidgets()
	{
		if (client.isResized())
		{
			return;
		}

		// Contract the view if it is expanded
		setViewSizeTo(EXPANDED_VIEW_HEIGHT, DEFAULT_VIEW_HEIGHT);
		setWidgetsSizeTo(EXPANDED_VIEW_HEIGHT, DEFAULT_VIEW_HEIGHT);

		// Show the chat messages widget again
		final Widget chatboxMessages = client.getWidget(WidgetInfo.CHATBOX_MESSAGES);

		if (chatboxMessages != null)
		{
			chatboxMessages.setHidden(false);
		}
	}

	private void updateConfig()
	{
		unregisterListeners();

		if (config.stretchedEnabled())
		{
			mouseManager.registerMouseListener(0, mouseListener);
			mouseManager.registerMouseWheelListener(0, mouseWheelListener);
		}

		client.setStretchedEnabled(config.stretchedEnabled());
		client.setStretchedIntegerScaling(config.integerScaling());
		client.setStretchedKeepAspectRatio(config.keepAspectRatio());
		client.setStretchedFast(config.increasedPerformance());
		resetWidgets();
	}

	private void unregisterListeners()
	{
		mouseManager.unregisterMouseListener(mouseListener);
		mouseManager.unregisterMouseWheelListener(mouseWheelListener);
	}
}
