package fr.lkdm.homecore.workbench;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ElectronicsBlock extends BaseEntityBlock {
    public static final MapCodec<ElectronicsBlock> CODEC = simpleCodec(ElectronicsBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public enum Part implements StringRepresentable {
        SINGLE("single"), LEFT("left"), RIGHT("right");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 15, 12, 15));

    public ElectronicsBlock(Properties properties) {
        super(properties);
        // Missing properties in older saves resolve to this compact, inventory-preserving state.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, 0).setValue(PART, Part.SINGLE));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos right = context.getClickedPos().relative(facing.getClockWise());
        Level level = context.getLevel();
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LEFT);
        BlockPlaceContext adjacent = BlockPlaceContext.at(context, right, context.getClickedFace());
        if (level.isOutsideBuildHeight(right) || !level.getWorldBorder().isWithinBounds(context.getClickedPos())
                || !level.getWorldBorder().isWithinBounds(right)
                || !level.getBlockState(right).canBeReplaced(adjacent)
                || !level.isUnobstructed(state.setValue(PART, Part.RIGHT), right,
                        context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer()))) return null;
        return state;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == Part.LEFT) {
            level.setBlock(pos.relative(state.getValue(FACING).getClockWise()), state.setValue(PART, Part.RIGHT), 3);
        }
    }
    public static BlockPos rootPosition(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.RIGHT ? pos.relative(state.getValue(FACING).getCounterClockWise()) : pos;
    }
    /** Resolves either visible half to the one persistent inventory. */
    public static ElectronicsBlockEntity workbench(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ElectronicsBlock)) return null;
        BlockPos root = rootPosition(pos, state);
        if (state.getValue(PART) == Part.RIGHT) {
            BlockState master = level.getBlockState(root);
            if (master.getBlock() != state.getBlock() || master.getValue(PART) != Part.LEFT
                    || master.getValue(FACING) != state.getValue(FACING)) return null;
        }
        return level.getBlockEntity(root) instanceof ElectronicsBlockEntity block ? block : null;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, PART);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.RIGHT ? null : new ElectronicsBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) == Part.RIGHT ? null
                : createTickerHelper(type, HomeCoreWorkbench.BLOCK_ENTITY.get(), ElectronicsBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ElectronicsBlockEntity workbench = workbench(level, pos);
        if (player instanceof ServerPlayer serverPlayer && workbench != null) {
            serverPlayer.openMenu(workbench, workbench.getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ElectronicsBlockEntity workbench) workbench.dropContents();
            if (!level.isClientSide && state.getValue(PART) != Part.SINGLE) {
                BlockPos partner = partnerPosition(pos, state);
                BlockState other = level.getBlockState(partner);
                if (isPartner(state, other)) level.destroyBlock(partner, true);
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Only LEFT supplies block loot. Remove it without block loot for a creative RIGHT break.
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == Part.RIGHT) {
            BlockPos partner = partnerPosition(pos, state);
            if (isPartner(state, level.getBlockState(partner))) level.setBlock(partner, Blocks.AIR.defaultBlockState(), 35);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static BlockPos partnerPosition(BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(PART) == Part.LEFT ? right : right.getOpposite());
    }

    private static boolean isPartner(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock() && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(PART) == (state.getValue(PART) == Part.LEFT ? Part.RIGHT : Part.LEFT);
    }
}
