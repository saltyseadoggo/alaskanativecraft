package com.github.platymemo.alaskanativecraft.client.renderer.entity;

import com.github.platymemo.alaskanativecraft.AlaskaNativeCraft;
import com.github.platymemo.alaskanativecraft.client.model.entity.DogsledEntityModel;
import com.github.platymemo.alaskanativecraft.entity.DogsledEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class DogsledEntityRenderer extends EntityRenderer<DogsledEntity> {
    private static final Identifier[] TEXTURES = new Identifier[]{new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/oak.png"),
                                                                  new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/spruce.png"),
                                                                  new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/birch.png"),
                                                                  new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/jungle.png"),
                                                                  new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/acacia.png"),
                                                                  new Identifier(AlaskaNativeCraft.MOD_ID, "textures/entity/dogsled/dark_oak.png")};
    protected final DogsledEntityModel model = new DogsledEntityModel();

    public DogsledEntityRenderer(EntityRenderDispatcher dispatcher) {
        super(dispatcher);
        this.shadowRadius = 0.5F;
    }

    public void render(DogsledEntity dogsledEntity, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light) {
        matrixStack.push();
        matrixStack.scale(1.5F, 1.5F, 1.5F);
        matrixStack.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(270 - yaw));
        float h = (float) dogsledEntity.getDamageWobbleTicks() - tickDelta;
        float j = dogsledEntity.getDamageWobbleStrength() - tickDelta;
        if (j < 0.0F) {
            j = 0.0F;
        }

        if (h > 0.0F) {
            matrixStack.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(MathHelper.sin(h) * h * j / 10.0F * (float) dogsledEntity.getDamageWobbleSide()));
        }

        // Chest Rendering
        matrixStack.push();
        float s = 0.5F;
        matrixStack.scale(s, s, s);
        matrixStack.translate(-0.3D, 0.6D, 0.5D);
        matrixStack.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(90.0F));
        this.renderBlock(Blocks.CHEST.getDefaultState(), matrixStack, vertexConsumerProvider, light);
        matrixStack.pop();

        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        matrixStack.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(90.0F));
        this.model.setAngles(dogsledEntity, tickDelta, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(this.model.getLayer(this.getTexture(dogsledEntity)));
        this.model.render(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);

        matrixStack.pop();
        super.render(dogsledEntity, yaw, tickDelta, matrixStack, vertexConsumerProvider, light);
    }

    @Override
    public Identifier getTexture(DogsledEntity dogsledEntity) {
        return TEXTURES[dogsledEntity.getDogsledType().ordinal()];
    }

    protected void renderBlock(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
    }
}
