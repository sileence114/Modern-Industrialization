package aztech.modern_industrialization.machinesv2.blockentities;

import aztech.modern_industrialization.api.energy.CableTier;
import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.api.energy.EnergyExtractable;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machinesv2.MachineBlockEntity;
import aztech.modern_industrialization.machinesv2.components.EnergyComponent;
import aztech.modern_industrialization.machinesv2.components.OrientationComponent;
import aztech.modern_industrialization.machinesv2.components.sync.EnergyBar;
import aztech.modern_industrialization.machinesv2.gui.MachineGuiParameters;
import aztech.modern_industrialization.machinesv2.helper.EnergyHelper;
import aztech.modern_industrialization.machinesv2.helper.OrientationHelper;
import aztech.modern_industrialization.machinesv2.models.MachineCasings;
import aztech.modern_industrialization.machinesv2.models.MachineModelClientData;
import aztech.modern_industrialization.util.RenderHelper;
import aztech.modern_industrialization.util.Simulation;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public class EnergyFromFluidMachineBlockEntity extends MachineBlockEntity implements Tickable {

    private final CableTier outputTier;
    private final long fluidConsumption;
    private final EnergyExtractable extractable;

    private final Predicate<Fluid> acceptedFluid;
    private final ToLongFunction<Fluid> fluidEUperMb;

    protected final MIInventory inventory;
    protected EnergyComponent energy;
    protected OrientationComponent orientation;
    protected boolean isActive;

    private EnergyFromFluidMachineBlockEntity(BlockEntityType<?> type,
                                             String name, CableTier outputTier,
                                             long energyCapacity, long fluidCapacity, long fluidConsumption,
                                             Predicate<Fluid> acceptedFluid, ToLongFunction<Fluid> fluidEUperMb, Fluid locked) {
        super(type, new MachineGuiParameters.Builder(name, false).build());
        this.outputTier = outputTier;
        this.energy = new EnergyComponent(energyCapacity);
        this.extractable = energy.buildExtractable((CableTier tier) -> tier == outputTier);
        this.fluidConsumption = fluidConsumption;
        EnergyBar.Parameters energyBarParams = new EnergyBar.Parameters(76, 39);
        registerClientComponent(new EnergyBar.Server(energyBarParams, energy::getEu, energy::getCapacity));
        this.orientation = new OrientationComponent(new OrientationComponent.Params(true, false, false));

        List<ConfigurableItemStack> itemStacks = new ArrayList<>();
        SlotPositions itemPositions =  SlotPositions.empty();

        this.acceptedFluid = acceptedFluid;
        this.fluidEUperMb = fluidEUperMb;

        List<ConfigurableFluidStack> fluidStacks;
        if(locked == null){
            fluidStacks = Collections.singletonList(ConfigurableFluidStack.standardInputSlot(81*fluidCapacity));
        }else{
            fluidStacks = Collections.singletonList(ConfigurableFluidStack.lockedInputSlot(81*fluidCapacity, locked));
        }

        SlotPositions fluidPositions = new SlotPositions.Builder().addSlot(25, 38).build();
        inventory = new MIInventory(itemStacks, fluidStacks, itemPositions, fluidPositions);

    }

    public EnergyFromFluidMachineBlockEntity(BlockEntityType<?> type,
                                             String name, CableTier outputTier,
                                             long energyCapacity, long fluidCapacity, long fluidConsumption,
                                             Predicate<Fluid> acceptedFluid, ToLongFunction<Fluid> fluidEUperMb) {
        this(type, name, outputTier, energyCapacity, fluidCapacity, fluidConsumption, acceptedFluid, fluidEUperMb, null);
    }

    public EnergyFromFluidMachineBlockEntity(BlockEntityType<?> type,
                                             String name, CableTier outputTier,
                                             long energyCapacity, long fluidCapacity, long fluidConsumption,
                                             Fluid acceptedFluid, long fluidEUperMb) {
        this(type, name, outputTier, energyCapacity, fluidCapacity, fluidConsumption, (Fluid f) -> ( f == acceptedFluid),
                (Fluid f) -> (fluidEUperMb), acceptedFluid);
    }


    @Override
    public MIInventory getInventory() {
        return inventory;
    }

    @Override
    protected ActionResult onUse(PlayerEntity player, Hand hand, Direction face) {
        return OrientationHelper.onUse(player, hand, face, orientation, this);
    }

    @Override
    protected MachineModelClientData getModelData() {
        MachineModelClientData data = new MachineModelClientData(MachineCasings.casingFromCableTier(outputTier));
        data.isActive = isActive;
        orientation.writeModelData(data);
        return data;
    }

    @Override
    public void onPlaced(LivingEntity placer, ItemStack itemStack) {
        orientation.onPlaced(placer, itemStack);
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
        orientation.readNbt(tag);
        isActive = tag.getBoolean("isActive");
        RenderHelper.forceChunkRemesh((ClientWorld) world, pos);
    }

    @Override
    public CompoundTag toClientTag(CompoundTag tag) {
        orientation.writeNbt(tag);
        tag.putBoolean("isActive", isActive);
        return tag;
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        super.toTag(tag);
        getInventory().writeNbt(tag);
        energy.writeNbt(tag);
        orientation.writeNbt(tag);
        tag.putBoolean("isActive", isActive);
        return tag;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        getInventory().readNbt(tag);
        energy.readNbt(tag);
        orientation.readNbt(tag);
        isActive = tag.getBoolean("isActive");
    }

    @Override
    public void tick() {
        if (world == null || world.isClient)
            return;

        boolean wasActive = isActive;
        ConfigurableFluidStack stack = inventory.fluidStacks.get(0);

        if (acceptedFluid.test(stack.getFluid())){
            long fuelEu = fluidEUperMb.applyAsLong(stack.getFluid());
            long fluidConsumed = Math.min(Math.min(energy.getRemainingCapacity() / fuelEu, stack.getAmount()/81),
                    this.fluidConsumption);
            if(fluidConsumed  > 0){
                stack.decrement(81*fluidConsumed);
                energy.insertEu(fluidConsumed * fuelEu, Simulation.ACT);
                isActive = true;
            }else{
                isActive = false;
            }

        }else{
            isActive = false;
        }

        EnergyHelper.autoOuput(this, orientation, outputTier, energy);

        if (wasActive != isActive) {
            sync();
        }
        markDirty();
    }

    public static void registerEnergyApi(BlockEntityType<?> bet) {
        EnergyApi.MOVEABLE.registerForBlockEntities((be, direction) -> {
            EnergyFromFluidMachineBlockEntity abe = (EnergyFromFluidMachineBlockEntity) be;
            if (abe.orientation.outputDirection == direction) {
                return abe.extractable;
            } else {
                return null;
            }
        }, bet);
    }
}
