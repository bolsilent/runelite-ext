/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Kamiel
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.menuentryswapper;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.events.WidgetMenuOptionClicked;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

@PluginDescriptor(
  name = "Menu Entry Swapper",
  enabledByDefault = false
)
public class MenuEntrySwapperPlugin extends Plugin
{
	private static final String CONFIGURE = "Configure";
	private static final String SAVE = "Save";
	private static final String RESET = "Reset";
	private static final String MENU_TARGET = "<col=ff9040>Shift-click";

	private static final String CONFIG_GROUP = "shiftclick";
	private static final String ITEM_KEY_PREFIX = "item_";

	private static final WidgetMenuOption FIXED_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
																							   MENU_TARGET, WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB);

	private static final WidgetMenuOption FIXED_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
																						  MENU_TARGET, WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB);

	private static final WidgetMenuOption RESIZABLE_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
																								   MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB);

	private static final WidgetMenuOption RESIZABLE_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
																							  MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB);

	private static final WidgetMenuOption RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
																											   MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB);

	private static final WidgetMenuOption RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
																										  MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB);

	@Inject
	private Client client;

	@Inject
	private MenuEntrySwapperConfig config;

	@Inject
	private ShiftClickInputListener inputListener;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MenuManager menuManager;

	@Getter
	private boolean configuringShiftClick = false;

	@Setter
	private boolean shiftModifier = false;

	@Provides
	MenuEntrySwapperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MenuEntrySwapperConfig.class);
	}

	@Override
	public void startUp()
	{
		if (config.shiftClickCustomization())
		{
			enableCustomization();
		}
	}

	@Override
	public void shutDown()
	{
		disableCustomization();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getKey().equals("shiftClickCustomization"))
		{
			if (config.shiftClickCustomization())
			{
				enableCustomization();
			}
			else
			{
				disableCustomization();
			}
		}
	}

	private Integer getSwapConfig(int itemId)
	{
		String config = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
		if (config == null || config.isEmpty())
		{
			return null;
		}

		return Integer.parseInt(config);
	}

	private void setSwapConfig(int itemId, int index)
	{
		configManager.setConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId, index);
	}

	private void unsetSwapConfig(int itemId)
	{
		configManager.unsetConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
	}

	private void enableCustomization()
	{
		keyManager.registerKeyListener(inputListener);
		refreshShiftClickCustomizationMenus();
	}

	private void disableCustomization()
	{
		keyManager.unregisterKeyListener(inputListener);
		removeShiftClickCustomizationMenus();
		configuringShiftClick = false;
	}

	@Subscribe
	public void onWidgetMenuOptionClicked(WidgetMenuOptionClicked event)
	{
		if (event.getWidget() == WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB
		  || event.getWidget() == WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB
		  || event.getWidget() == WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB)
		{
			configuringShiftClick = event.getMenuOption().equals(CONFIGURE);
			refreshShiftClickCustomizationMenus();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!configuringShiftClick)
		{
			return;
		}

		MenuEntry firstEntry = event.getFirstEntry();
		if (firstEntry == null)
		{
			return;
		}

		int widgetId = firstEntry.getParam1();
		if (widgetId != WidgetInfo.INVENTORY.getId())
		{
			return;
		}

		int itemId = firstEntry.getIdentifier();
		if (itemId == -1)
		{
			return;
		}

		ItemComposition itemComposition = client.getItemDefinition(itemId);
		String itemName = itemComposition.getName();
		String option = "Use";
		int shiftClickActionindex = itemComposition.getShiftClickActionIndex();
		String[] inventoryActions = itemComposition.getInventoryActions();

		if (shiftClickActionindex >= 0 && shiftClickActionindex < inventoryActions.length)
		{
			option = inventoryActions[shiftClickActionindex];
		}

		MenuEntry[] entries = event.getMenuEntries();

		for (MenuEntry entry : entries)
		{
			if (itemName.equals(Text.removeTags(entry.getTarget())))
			{
				entry.setType(MenuAction.RUNELITE.getId());

				if (option.equals(entry.getOption()))
				{
					entry.setOption("* " + option);
				}
			}
		}

		final MenuEntry resetShiftClickEntry = new MenuEntry();
		resetShiftClickEntry.setOption(RESET);
		resetShiftClickEntry.setTarget(MENU_TARGET);
		resetShiftClickEntry.setIdentifier(itemId);
		resetShiftClickEntry.setParam1(widgetId);
		resetShiftClickEntry.setType(MenuAction.RUNELITE.getId());
		client.setMenuEntries(ArrayUtils.addAll(entries, resetShiftClickEntry));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.RUNELITE || event.getWidgetId() != WidgetInfo.INVENTORY.getId())
		{
			return;
		}

		int itemId = event.getId();

		if (itemId == -1)
		{
			return;
		}

		String option = event.getMenuOption();
		String target = event.getMenuTarget();
		ItemComposition itemComposition = client.getItemDefinition(itemId);

		if (option.equals(RESET) && target.equals(MENU_TARGET))
		{
			unsetSwapConfig(itemId);
			itemComposition.resetShiftClickActionIndex();
			return;
		}

		if (!itemComposition.getName().equals(Text.removeTags(target)))
		{
			return;
		}

		int index = -1;
		boolean valid = false;

		if (option.equals("Use")) //because "Use" is not in inventoryActions
		{
			valid = true;
		}
		else
		{
			String[] inventoryActions = itemComposition.getInventoryActions();

			for (index = 0; index < inventoryActions.length; index++)
			{
				if (option.equals(inventoryActions[index]))
				{
					valid = true;
					break;
				}
			}
		}

		if (valid)
		{
			setSwapConfig(itemId, index);
			itemComposition.setShiftClickActionIndex(index);
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int itemId = event.getIdentifier();
		String option = Text.removeTags(event.getOption()).toLowerCase();
		String target = Text.removeTags(event.getTarget()).toLowerCase();

		if (option.equals("talk-to"))
		{
			if (config.swapPickpocket())
			{
				swap("pickpocket", option, target, true);
			}

			if (config.swapAbyssTeleport() && target.contains("mage of zamorak"))
			{
				swap("teleport", option, target, true);
			}

			if (config.swapBank())
			{
				swap("bank", option, target, true);
			}

			if (config.swapExchange())
			{
				swap("exchange", option, target, true);
			}

			// make sure assignment swap is higher priority than trade swap for slayer masters
			if (config.swapAssignment())
			{
				swap("assignment", option, target, true);
			}

			if (config.swapTrade())
			{
				swap("trade", option, target, true);
			}

			if (config.claimSlime() && target.equals("robin"))
			{
				swap("claim-slime", option, target, true);
			}

			if (config.swapTravel())
			{
				swap("travel", option, target, true);
				swap("pay-fare", option, target, true);
				swap("charter", option, target, true);
				swap("take-boat", option, target, true);
				swap("fly", option, target, true);
				swap("jatizso", option, target, true);
				swap("neitiznot", option, target, true);
				swap("rellekka", option, target, true);
				swap("follow", option, target, true);
				swap("transport", option, target, true);
			}

			if (config.swapPay())
			{
				swap("pay", option, target, true);
			}

			if (config.swapDecant())
			{
				swap("decant", option, target, true);
			}
		}
		else if (config.swapTravel() && option.equals("pass") && target.equals("energy barrier"))
		{
			swap("pay-toll(2-ecto)", option, target, true);
		}
		else if (config.swapTravel() && option.equals("open") && target.equals("gate"))
		{
			swap("pay-toll(10gp)", option, target, true);
		}
		else if (config.swapTravel() && option.equals("inspect") && target.equals("trapdoor"))
		{
			swap("travel", option, target, true);
		}
		else if (config.swapHarpoon() && option.equals("cage"))
		{
			swap("harpoon", option, target, true);
		}
		else if (config.swapHarpoon() && (option.equals("big net") || option.equals("net")))
		{
			swap("harpoon", option, target, true);
		}
		else if (config.swapHomePortal() != HouseMode.ENTER && option.equals("enter"))
		{
			switch (config.swapHomePortal())
			{
				case HOME:
					swap("home", option, target, true);
					break;
				case BUILD_MODE:
					swap("build mode", option, target, true);
					break;
				case FRIENDS_HOUSE:
					swap("friend's house", option, target, true);
					break;
			}
		}
		else if (config.swapFairyRing() != FairyRingMode.ZANARIS && (option.equals("zanaris") || option.equals("tree")))
		{
			if (config.swapFairyRing() == FairyRingMode.LAST_DESTINATION)
			{
				swap("last-destination (", option, target, false);
			}
			else if (config.swapFairyRing() == FairyRingMode.CONFIGURE)
			{
				swap("configure", option, target, false);
			}
		}
		else if (config.swapBoxTrap() && (option.equals("check") || option.equals("dismantle")))
		{
			swap("reset", option, target, true);
		}
		else if (config.swapBoxTrap() && option.equals("take"))
		{
			swap("lay", option, target, true);
		}
		else if (config.swapChase() && option.equals("pick-up"))
		{
			swap("chase", option, target, true);
		}
		else if (config.depositX() && option.equals("deposit-1"))
		{
			swap("(deposit-(?!1$|5$|10$)[0-9]*)", "(deposit-1)", target);
		}
		else if (config.withdrawX() && option.equals("withdraw-1"))
		{
			swap("(withdraw-(?!1$|5$|10$)[0-9]*)", "(withdraw-1)", target);
		}
		else if (config.construction() && target.equals("larder") && (option.equals("search") || option.equals("examine")))
		{
			remove(target, "search", "walk here", "examine");
		}
		else if (config.construction() && target.equals("larder space") && option.equals("examine"))
		{
			remove(target, "walk here", "examine");
		}
		else if (config.shiftClickCustomization() && shiftModifier && !option.equals("use"))
		{
			Integer customOption = getSwapConfig(itemId);

			if (customOption != null && customOption == -1)
			{
				swap("use", option, target, true);
			}
		}
		// Put all item-related swapping after shift-click
		else if (config.swapTeleportItem() && option.equals("wear"))
		{
			swap("rub", option, target, true);
			swap("teleport", option, target, true);
		}
		else if (option.equals("wield"))
		{
			if (config.swapTeleportItem())
			{
				swap("teleport", option, target, true);
			}
		}
		else if (config.swapBones() && option.equals("bury"))
		{
			swap("use", option, target, true);
		}
		else if (config.swapBirdhouseEmpty() && option.equals("interact") && target.contains("birdhouse"))
		{
			swap("empty", option, target, true);
		}
	}

	@Subscribe
	public void onPostItemComposition(PostItemComposition event)
	{
		ItemComposition itemComposition = event.getItemComposition();
		Integer option = getSwapConfig(itemComposition.getId());

		if (option != null)
		{
			itemComposition.setShiftClickActionIndex(option);

			// Update our cached item composition too
			ItemComposition ourItemComposition = itemManager.getItemComposition(itemComposition.getId());
			ourItemComposition.setShiftClickActionIndex(option);
		}
	}

	private int searchIndex(MenuEntry[] entries, String option, String target, boolean strict)
	{
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			String entryOption = Text.removeTags(entry.getOption()).toLowerCase();
			String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();

			if (strict)
			{
				if (entryOption.equals(option) && entryTarget.equals(target))
				{
					return i;
				}
			}
			else
			{
				if (entryOption.contains(option.toLowerCase()) && entryTarget.equals(target))
				{
					return i;
				}
			}
		}

		return -1;
	}

	private int searchIndex(MenuEntry[] entries, String regex, String target)
	{
		Pattern pattern = Pattern.compile(regex);
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			String entryOption = Text.removeTags(entry.getOption()).toLowerCase();
			String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();
			Matcher matcher = pattern.matcher(entryOption);
			if (matcher.matches() && entryTarget.equals(target))
			{
				return i;
			}
		}

		return -1;
	}

	private void swap(String optionA, String optionB, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();

		int idxA = searchIndex(entries, optionA, target, strict);
		int idxB = searchIndex(entries, optionB, target, strict);

		if (idxA >= 0 && idxB >= 0)
		{
			MenuEntry entry = entries[idxA];
			entries[idxA] = entries[idxB];
			entries[idxB] = entry;

			client.setMenuEntries(entries);
		}
	}

	private void swap(String regexA, String regexB, String target)
	{
		MenuEntry[] entries = client.getMenuEntries();

		int idxA = searchIndex(entries, regexA, target);
		int idxB = searchIndex(entries, regexB, target);

		if (idxA >= 0 && idxB >= 0)
		{
			MenuEntry entry = entries[idxA];
			entries[idxA] = entries[idxB];
			entries[idxB] = entry;

			client.setMenuEntries(entries);
		}
	}

	private void remove(String target, String... options)
	{
		MenuEntry[] entries = client.getMenuEntries();
		boolean hasTarget = false;
		for (MenuEntry entry : entries)
		{
			if (Text.removeTags(entry.getTarget()).toLowerCase().equals(target))
			{
				hasTarget = true;
				break;
			}
		}
		if (hasTarget)
		{
			List<MenuEntry> validEntries = new ArrayList<>();
			List<String> opts = Arrays.asList(options);
			for (MenuEntry entry : entries)
			{
				if (!opts.contains(Text.removeTags(entry.getOption()).toLowerCase()))
				{
					validEntries.add(entry);
				}
			}
			client.setMenuEntries(validEntries.toArray(new MenuEntry[validEntries.size()]));
		}
	}

	private void removeShiftClickCustomizationMenus()
	{
		menuManager.removeManagedCustomMenu(FIXED_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(FIXED_INVENTORY_TAB_SAVE);
		menuManager.removeManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE);
		menuManager.removeManagedCustomMenu(RESIZABLE_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(RESIZABLE_INVENTORY_TAB_SAVE);
	}

	private void refreshShiftClickCustomizationMenus()
	{
		removeShiftClickCustomizationMenus();
		if (configuringShiftClick)
		{
			menuManager.addManagedCustomMenu(FIXED_INVENTORY_TAB_SAVE);
			menuManager.addManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE);
			menuManager.addManagedCustomMenu(RESIZABLE_INVENTORY_TAB_SAVE);
		}
		else
		{
			menuManager.addManagedCustomMenu(FIXED_INVENTORY_TAB_CONFIGURE);
			menuManager.addManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE);
			menuManager.addManagedCustomMenu(RESIZABLE_INVENTORY_TAB_CONFIGURE);
		}
	}

	Collection<WidgetItem> getInventoryItems()
	{
		return Collections.unmodifiableCollection(client.getWidget(WidgetInfo.INVENTORY).getWidgetItems());
	}
}
