package hu.astralixmc.hud.ui;

import hu.astralixmc.hud.Hud;
import hu.astralixmc.hud.Platform;
import hu.astralixmc.hud.module.Ctx;
import hu.astralixmc.hud.render.Draw;
import org.lwjgl.glfw.GLFW;

/**
 * Áttűnések a saját és az eredeti Minecraft főmenü között, valamint
 * az AstralixMC felirat elrejtése, amíg a TAB-lista nyitva van.
 */
public final class Transitions {
   /** Saját menü -> sötétbe úsztatás hossza (ms). */
   private static final long LEAVE_MS = 320L;
   /** Eredeti menüből visszatérve a sötétből előtűnés hossza (ms). */
   private static final long RETURN_MS = 450L;
   /** Biztonsági korlát: ha a váltás valamiért elmarad, ennyi után visszaáll a menü. */
   private static final long LEAVE_TIMEOUT_MS = 3000L;

   private static volatile long leaveAt;
   private static volatile long returnAt;

   private Transitions() {
   }

   /** Az "Eredeti Minecraft menü" gomb: előbb elsötétít, a váltás a következő tickben történik. */
   public static void leave() {
      if (leaveAt == 0L) {
         leaveAt = System.currentTimeMillis();
      }
   }

   /** Hud.leaveVanillaMenu() elején hívódik: a saját menü sötétből tűnik elő. */
   public static void returning() {
      leaveAt = 0L;
      returnAt = System.currentTimeMillis();
   }

   /** Hud.tick() elején hívódik. */
   public static void tick() {
      long at = leaveAt;
      if (at != 0L) {
         long el = System.currentTimeMillis() - at;
         Platform p = Hud.platform();
         if (p == null || p.inWorld() || el > LEAVE_TIMEOUT_MS) {
            leaveAt = 0L;
         } else if (el >= LEAVE_MS) {
            leaveAt = 0L;
            Hud.openVanillaMenu();
         }
      }
   }

   /** A TitleUi.draw() végén hívódik (a TitleUi transzformált koordinátarendszerében). */
   public static void overlay(TitleUi ui) {
      try {
         long now = System.currentTimeMillis();
         int alpha = 0;
         long at = leaveAt;
         if (at != 0L) {
            float t = Math.min(1.0F, (float)(now - at) / LEAVE_MS);
            alpha = Math.round(255.0F * t * t * (3.0F - 2.0F * t));
         }

         long ret = returnAt;
         if (ret != 0L) {
            float t = (float)(now - ret) / RETURN_MS;
            if (t >= 1.0F) {
               returnAt = 0L;
            } else {
               alpha = Math.max(alpha, Math.round(255.0F * (1.0F - Draw.ease(t))));
            }
         }

         if (alpha <= 0 || ui.g == null) {
            return;
         }

         float sc = ui.sc > 0.0F ? ui.sc : 1.0F;
         int x1 = (int)Math.floor(-ui.ox / sc) - 2;
         int y1 = (int)Math.floor(-ui.oy / sc) - 2;
         int x2 = (int)Math.ceil((ui.W - ui.ox) / sc) + 2;
         int y2 = (int)Math.ceil((ui.H - ui.oy) / sc) + 2;
         ui.g.fill(x1, y1, x2, y2, Draw.withAlpha(0, alpha));
         if (at != 0L) {
            // Elsötétítés közben ne lehessen mást megnyomni.
            ui.block(x1, y1, x2 - x1, y2 - y1);
         }
      } catch (Throwable ignored) {
      }
   }

   /** BrandModule.layout() elején hívódik: TAB lenyomva (játékban, nyitott képernyő nélkül) -> rejtve. */
   public static boolean hideBrand(Ctx c) {
      try {
         if (c == null || c.preview || c.p == null || !c.p.inWorld() || c.p.screenOpen()) {
            return false;
         }

         long win = GLFW.glfwGetCurrentContext();
         return win != 0L && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
      } catch (Throwable ignored) {
         return false;
      }
   }
}
