package com.tom.createores.block.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.recipe.DrillingRecipe;
import com.tom.createores.util.IOBlockType;
import com.tom.createores.util.QueueInventory;

public class DrillBlockEntity extends ExcavatingBlockEntity<DrillingRecipe> {
	private QueueInventory inventory;
	private FluidTank fluidTank;
	private LazyOptional<FluidTank> tankCap;

	public DrillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new QueueInventory();
		fluidTank = new FluidTank(16000, v -> current != null && current.getDrillingFluid().test(v)) {

			@Override
			protected void onContentsChanged() {
				notifyUpdate();
			}
		};
		tankCap = LazyOptional.of(() -> fluidTank);
	}

	@Override
	public <T> LazyOptional<T> getCaps(Capability<T> cap, IOBlockType type) {
		if(type == IOBlockType.ITEM_OUT && cap == ForgeCapabilities.ITEM_HANDLER) {
			return inventory.asCap();
		}

		if(type == IOBlockType.FLUID_IN && cap == ForgeCapabilities.FLUID_HANDLER) {
			return tankCap.cast();
		}
		return LazyOptional.empty();
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		fluidTank.readFromNBT(tag.getCompound("tank"));
		if(!clientPacket) {
			inventory.load(tag.getList("inv", Tag.TAG_COMPOUND));
		}
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.put("tank", fluidTank.writeToNBT(new CompoundTag()));
		if(!clientPacket) {
			tag.put("inv", inventory.toTag());
		}
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		containedFluidTooltip(tooltip, isPlayerSneaking, tankCap.cast());
		return true;
	}

	@Override
	public void addToGoggleTooltip(List<Component> tooltip, DrillingRecipe rec) {
		if(rec.getDrillingFluid().getRequiredAmount() != 0 && (!rec.getDrillingFluid().test(fluidTank.getFluid()) || fluidTank.getFluidAmount() < rec.getDrillingFluid().getRequiredAmount())) {
			tooltip.add(Component.literal(spacing).append(Component.translatable("info.coe.drill.noFluid")));
		}
	}

	@Override
	protected boolean validateRecipe(DrillingRecipe recipe) {
		return super.validateRecipe(recipe) && (recipe.getDrillingFluid().getRequiredAmount() == 0 || recipe.getDrillingFluid().test(fluidTank.getFluid()));
	}

	@Override
	public void invalidate() {
		super.invalidate();
		inventory.invalidate();
		tankCap.invalidate();
	}

	@Override
	public void dropInv() {
		super.dropInv();
		for(int i = 0; i < inventory.getSlots(); ++i) {
			dropItemStack(inventory.getStackInSlot(i));
		}
	}

	@Override
	protected boolean canExtract() {
		return inventory.hasSpace() && current.getDrillingFluid().getRequiredAmount() == 0 ||
				(current.getDrillingFluid().test(fluidTank.getFluid()) &&
						fluidTank.getFluidAmount() >= current.getDrillingFluid().getRequiredAmount());
	}

	@Override
	protected void onFinished() {
		current.getOutput().stream().map(ProcessingOutput::rollOutput).filter(i -> !i.isEmpty()).forEach(inventory::add);
		fluidTank.drain(current.getDrillingFluid().getRequiredAmount(), FluidAction.EXECUTE);
	}

	@Override
	protected RecipeType<DrillingRecipe> getRecipeType() {
		return CreateOreExcavation.DRILLING_RECIPES.getRecipeType();
	}
}
