package slimeknights.mantle.plugin.jei.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.RequiredArgsConstructor;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renderer for entity type ingredients
 */
@RequiredArgsConstructor
public class EntityIngredientRenderer implements IIngredientRenderer<EntityIngredient.EntityInput> {
  private static final ResourceLocation MISSING = Mantle.getResource("textures/item/missingno.png");
  /** Entity types that will not render, as they either errored or are the wrong type */
  private static final Set<EntityType<?>> IGNORED_ENTITIES = new HashSet<>();

  /** Square size of the renderer in pixels */
  private final int size;

  /** Cache of entities for each entity type */
  private final Map<EntityType<?>,Entity> ENTITY_MAP = new HashMap<>();

  @Override
  public int getWidth() {
    return size;
  }

  @Override
  public int getHeight() {
    return size;
  }

  @Override
  public void render(GuiGraphics graphics, @Nullable EntityIngredient.EntityInput input) {
    if (input != null) {
      Level world = Minecraft.getInstance().level;
      EntityType<?> type = input.type();
      if (world != null && !IGNORED_ENTITIES.contains(type)) {
        Entity entity;
        // players cannot be created using the type, but we can use the client player
        // side effect is it renders armor/items
        if (type == EntityType.PLAYER) {
          entity = Minecraft.getInstance().player;
        } else {
          // entity is created with the client world, but the entity map is thrown away when JEI restarts so they should be okay I think
          entity = ENTITY_MAP.computeIfAbsent(type, t -> t.create(world));
        }
        // only can draw living entities, plus non-living ones don't get recipes anyways
        if (entity instanceof LivingEntity livingEntity) {
          // scale down large mobs, but don't scale up small ones
          int scale = size / 2;
          float height = entity.getBbHeight();
          float width = entity.getBbWidth();
          if (height > 2 || width > 2) {
            scale = (int)(size / Math.max(height, width));
          }
          // catch exceptions drawing the entity to be safe, any caught exceptions blacklist the entity
          try {
            renderEntity(graphics, livingEntity, scale);
            return;
          } catch (Exception e) {
            Mantle.logger.error("Error drawing entity " + BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
            IGNORED_ENTITIES.add(type);
            ENTITY_MAP.remove(type);
          }
        } else {
          // not living, so might as well skip next time
          IGNORED_ENTITIES.add(type);
          ENTITY_MAP.remove(type);
        }
      }

      // fallback, draw a pink and black "spawn egg"
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1, 1, 1, 1);
      int offset = (size - 16) / 2;
      graphics.blit(MISSING, offset, offset, 0, 0, 16, 16, 16, 16);
    }
  }

  /** Renders without the screen-space scissor used by {@code renderEntityInInventoryFollowsMouse}. */
  private void renderEntity(GuiGraphics graphics, LivingEntity entity, int scale) {
    float bodyRotation = entity.yBodyRot;
    float rotation = entity.getYRot();
    float xRotation = entity.getXRot();
    float previousHeadRotation = entity.yHeadRotO;
    float headRotation = entity.yHeadRot;

    entity.yBodyRot = 180.0F;
    entity.setYRot(180.0F);
    entity.setXRot(0.0F);
    entity.yHeadRotO = 180.0F;
    entity.yHeadRot = 180.0F;

    try {
      Quaternionf camera = new Quaternionf();
      Quaternionf pose = new Quaternionf().rotateZ((float)Math.PI);
      Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
      InventoryScreen.renderEntityInInventory(graphics, size / 2.0F, size / 2.0F, scale / entity.getScale(), translation, pose, camera, entity);
    } finally {
      entity.yBodyRot = bodyRotation;
      entity.setYRot(rotation);
      entity.setXRot(xRotation);
      entity.yHeadRotO = previousHeadRotation;
      entity.yHeadRot = headRotation;
    }
  }

  @Override
  public List<Component> getTooltip(EntityIngredient.EntityInput type, TooltipFlag flag) {
    List<Component> tooltip = new ArrayList<>();
    tooltip.add(type.type().getDescription());
    if (flag.isAdvanced()) {
      tooltip.add((Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type.type()).toString())).withStyle(ChatFormatting.DARK_GRAY));
    }
    return tooltip;
  }
}
