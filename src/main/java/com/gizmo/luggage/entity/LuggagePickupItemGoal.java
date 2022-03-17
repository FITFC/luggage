package com.gizmo.luggage.entity;

import com.gizmo.luggage.Registries;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.pathfinding.PathNavigator;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class LuggagePickupItemGoal extends Goal {

	private final LuggageEntity luggage;
	private final PathNavigator navigation;
	@Nullable
	private ItemEntity targetItem = null;

	public LuggagePickupItemGoal(LuggageEntity luggage) {
		this.luggage = luggage;
		this.navigation = luggage.getNavigation();
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		//we only want the luggage to pick up items if it isn't currently pathing to us
		if (!navigation.isDone()) return false;

		//sort through items, get the closest one
		List<ItemEntity> items = this.luggage.level.getEntitiesOfClass(ItemEntity.class, this.luggage.getBoundingBox().inflate(16.0D), item ->
				(item.isOnGround() || item.isInWater()) &&
						this.luggage.getInventory().canAddItem(item.getItem()) &&
						item.getItem().getItem() != Registries.ItemRegistry.LUGGAGE.get() &&
						item.getItem().getItem() != Items.SHULKER_BOX);
		items.sort(Comparator.comparingDouble(this.luggage::distanceToSqr));

		if (!items.isEmpty()) {
			this.targetItem = items.get(0);
			return true;
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return this.luggage.isAlive() && !this.navigation.isDone() && !this.navigation.isStuck() && this.targetItem != null && this.targetItem.isAlive();
	}

	@Override
	public void start() {
		if (this.targetItem != null) {
			this.navigation.moveTo(this.targetItem, 1.2D);
			this.luggage.tryingToFetchItem = true;
		}
	}

	@Override
	public void stop() {
		this.luggage.tryingToFetchItem = false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!luggage.level.isClientSide) {
			if (this.targetItem != null && this.luggage.distanceToSqr(this.targetItem.position()) < 2.5D) {
				ItemStack item = this.targetItem.getItem();
				if (this.luggage.getInventory().canAddItem(this.targetItem.getItem())) {
					if (this.luggage.lastSound > 15) {
						boolean isFood = item.getUseAnimation() == UseAction.EAT;
						this.luggage.playSound(isFood ? Registries.SoundRegistry.LUGGAGE_EAT_FOOD : Registries.SoundRegistry.LUGGAGE_EAT_ITEM,
								0.5F, 1.0F + (this.luggage.getRandom().nextFloat() * 0.2F));
						this.luggage.lastSound = 0;
					}

					//stole this from Villager.pickUpItem lol
					Inventory simplecontainer = this.luggage.getInventory();
					boolean flag = simplecontainer.canAddItem(item);
					if (!flag) {
						return;
					}

					this.luggage.onItemPickup(this.targetItem);
					this.luggage.take(this.targetItem, item.getCount());
					ItemStack consumedStack = simplecontainer.addItem(item);
					if (consumedStack.isEmpty()) {
						this.targetItem.remove();
					} else {
						item.setCount(consumedStack.getCount());
					}
				}
			}
		}
	}
}
