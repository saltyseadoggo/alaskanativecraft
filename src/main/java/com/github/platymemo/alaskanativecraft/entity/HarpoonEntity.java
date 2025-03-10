package com.github.platymemo.alaskanativecraft.entity;

import com.github.platymemo.alaskanativecraft.AlaskaNativeCraft;
import com.github.platymemo.alaskanativecraft.item.HarpoonItem;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class HarpoonEntity extends PersistentProjectileEntity {
    public static final Identifier SPAWN_PACKET = new Identifier(AlaskaNativeCraft.MOD_ID, "harpoon_entity");
    private ItemStack harpoonStack;
    private static final TrackedData<Boolean> ENCHANTED;
    private HarpoonEntity.State state;
    private boolean dealtDamage;

    public HarpoonEntity(EntityType<? extends HarpoonEntity> entityType, World world, HarpoonItem item) {
        super(entityType, world);
        this.state = HarpoonEntity.State.FLYING;
        this.harpoonStack = new ItemStack(item);
    }

    public HarpoonEntity(World world, LivingEntity owner, HarpoonItem item, ItemStack stack) {
        super(item.getType(), owner, world);
        this.harpoonStack = stack.copy();
        this.state = HarpoonEntity.State.FLYING;
        this.dataTracker.set(ENCHANTED, stack.hasGlint());
    }

    @Environment(EnvType.CLIENT)
    public HarpoonEntity(World world, double x, double y, double z, HarpoonItem item) {
        super(item.getType(), x, y, z, world);
        this.state = HarpoonEntity.State.FLYING;
        this.harpoonStack = new ItemStack(item);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ENCHANTED, false);
    }

    @Override
    public Packet<?> createSpawnPacket() {
        PacketByteBuf packet = new PacketByteBuf(Unpooled.buffer());

        packet.writeVarInt(Registry.ENTITY_TYPE.getRawId(this.getType()));
        packet.writeUuid(this.getUuid());
        packet.writeVarInt(this.getEntityId());
        packet.writeDouble(this.getX());
        packet.writeDouble(this.getY());
        packet.writeDouble(this.getZ());
        packet.writeByte(MathHelper.floor(this.pitch * 256.0F / 360.0F));
        packet.writeByte(MathHelper.floor(this.yaw * 256.0F / 360.0F));

        return ServerSidePacketRegistry.INSTANCE.toPacket(SPAWN_PACKET, packet);
    }

    protected ItemStack asItemStack() {
        return this.harpoonStack.copy();
    }

    @Environment(EnvType.CLIENT)
    public boolean isEnchanted() {
        return this.dataTracker.get(ENCHANTED);
    }

    @Nullable
    protected EntityHitResult getEntityCollision(Vec3d currentPosition, Vec3d nextPosition) {
        return this.dealtDamage ? null : super.getEntityCollision(currentPosition, nextPosition);
    }

    @Override
    public void tick() {
        if (this.state == State.BOBBING) {
            float fluidHeight = 0.0F;
            BlockPos blockPos = this.getBlockPos();
            FluidState fluidState = this.world.getFluidState(blockPos);
            if (fluidState.isIn(FluidTags.WATER)) {
                fluidHeight = fluidState.getHeight(this.world, blockPos);
            }
            Vec3d vec3d = this.getVelocity();
            double distanceFromLiquidHeight = this.getY() + vec3d.y - (double)blockPos.getY() - (double)fluidHeight;

            // Get pitch, zero it if its minimal.
            if (vec3d.y > 0.032D) {
                this.pitch = (float)(MathHelper.atan2(vec3d.y, MathHelper.sqrt(squaredHorizontalLength(vec3d))) * 57.2957763671875D);
                this.pitch = updateRotation(this.prevPitch, this.pitch);
            }
            else {
                this.pitch = updateRotation(this.prevPitch, 0.0F);
            }

            // Get bobbing action
            if (Math.abs(distanceFromLiquidHeight) < 0.01D) {
                distanceFromLiquidHeight += Math.signum(distanceFromLiquidHeight) * 0.1D;
            }
            this.setVelocity(vec3d.x * 0.9D, vec3d.y - distanceFromLiquidHeight * (double)this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
            vec3d = this.getVelocity();
            this.updatePosition(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z);
            this.checkBlockCollision();
            return;
        }
        super.tick();
        boolean inWater = this.isTouchingWater();

        // We don't change anything unless the Harpoon is under water
        if (this.inGround && this.state != State.LANDED) {
            this.state = State.LANDED;
            return;
        }
        if (!inWater || this.state != State.FLYING) { return; }

        // Revert previous calculations
        Vec3d velocity = this.getVelocity();
        double d = velocity.x;
        double e = velocity.y + 0.05000000074505806D;
        double g = velocity.z;
        this.setVelocity(d, e, g);

        double h = this.getX() - d;
        double j = this.getY() - e;
        double k = this.getZ() - g;

        // Now do regular tick stuff
        float f = 0.0F;
        BlockPos blockPos = this.getBlockPos();
        FluidState fluidState = this.world.getFluidState(blockPos);
        if (fluidState.isIn(FluidTags.WATER)) {
            f = fluidState.getHeight(this.world, blockPos);
        }
        velocity = this.getVelocity();
        double distanceFromLiquidHeight = this.getY() + velocity.y - (double)blockPos.getY() - (double)f;

        // The harpoon is still flying through water, so make it buoyant
        if (distanceFromLiquidHeight > 0.0D && velocity.length() < 1) {
            this.state = State.BOBBING;
        }
        else
            this.setVelocity(velocity.x, velocity.y + 0.02500000037252903D, velocity.z);

        velocity = this.getVelocity().multiply(this.getDragInWater());
        h += velocity.x;
        j += velocity.y;
        k += velocity.z;
        this.updatePosition(h, j, k);
        this.checkBlockCollision();
    }

    @Override
    protected void checkBlockCollision() {
        super.checkBlockCollision();
        if (this.inGround) {
            this.state = State.LANDED;
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        float f = ((HarpoonItem) this.harpoonStack.getItem()).getAttackDamage();
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity)entity;
            f += EnchantmentHelper.getAttackDamage(this.harpoonStack, livingEntity.getGroup());
        }

        Entity entity2 = this.getOwner();
        DamageSource damageSource = createHarpoonDamageSource(this, (entity2 == null ? this : entity2));
        this.dealtDamage = true;
        SoundEvent soundEvent = SoundEvents.ITEM_TRIDENT_HIT;
        if (entity.damage(damageSource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity2 = (LivingEntity)entity;
                if (entity2 instanceof LivingEntity) {
                    EnchantmentHelper.onUserDamaged(livingEntity2, entity2);
                    EnchantmentHelper.onTargetDamaged((LivingEntity)entity2, livingEntity2);
                }

                this.onHit(livingEntity2);

                if (entity instanceof MobEntity && this.getOwner() instanceof PlayerEntity && ((MobEntity) entity).canBeLeashedBy((PlayerEntity) this.getOwner()) && !((MobEntity) entity).isLeashed() && this.harpoonStack.getTag().contains("leashed") && this.harpoonStack.getTag().getBoolean("leashed")) {
                    ((MobEntity) entity).attachLeash(this.getOwner(), true);
                    this.harpoonStack.removeSubTag("leashed");
                    this.setVelocity(Vec3d.ZERO);
                    this.playSound(soundEvent, 1.0F, 1.0F);
                    return;
                }
            }
        }

        this.setVelocity(this.getVelocity().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(soundEvent, 1.0F, 1.0F);
    }

    protected SoundEvent getHitSound() {
        return SoundEvents.ITEM_TRIDENT_HIT_GROUND;
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        Entity entity = this.getOwner();
        if (entity == null || entity.getUuid() == player.getUuid()) {
            if (this.state == State.FLYING) { return; }
            else if (!this.world.isClient && (this.state == State.BOBBING || this.isNoClip()) && this.shake <= 0) {
                boolean bl = this.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED || this.pickupType == PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY && player.abilities.creativeMode || this.isNoClip() && this.getOwner().getUuid() == player.getUuid();
                if (this.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED && !player.inventory.insertStack(this.asItemStack())) {
                    bl = false;
                }

                if (bl) {
                    player.sendPickup(this, 1);
                    this.remove();
                }
            }
            else
                super.onPlayerCollision(player);
        }
    }

    public void readCustomDataFromTag(CompoundTag tag) {
        super.readCustomDataFromTag(tag);
        if (tag.contains("Trident", 10)) {
            this.harpoonStack = ItemStack.fromTag(tag.getCompound("Harpoon"));
        }

        this.dealtDamage = tag.getBoolean("DealtDamage");
    }

    @Override
    public void writeCustomDataToTag(CompoundTag tag) {
        super.writeCustomDataToTag(tag);
        tag.put("Harpoon", this.harpoonStack.toTag(new CompoundTag()));
        tag.putBoolean("DealtDamage", this.dealtDamage);
    }

    public void age() {
        if (this.pickupType != PersistentProjectileEntity.PickupPermission.ALLOWED) {
            super.age();
        }
    }

    protected float getDragInWater() {
        return 0.9F;
    }

    @Environment(EnvType.CLIENT)
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    public static DamageSource createHarpoonDamageSource(Entity harpoon, Entity owner) {
        return new ProjectileDamageSource("harpoon", harpoon, owner).setProjectile();
    }

    static {
        ENCHANTED = DataTracker.registerData(TridentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    }

    enum State {
        FLYING,
        HOOKED_IN_ENTITY,
        BOBBING,
        LANDED
    }
}
