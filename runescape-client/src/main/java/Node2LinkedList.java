import net.runelite.mapping.Export;
import net.runelite.mapping.Implements;
import net.runelite.mapping.ObfuscatedName;
import net.runelite.mapping.ObfuscatedSignature;

@ObfuscatedName("gi")
@Implements("Node2LinkedList")
public final class Node2LinkedList {
   @ObfuscatedName("o")
   @ObfuscatedSignature(
      signature = "Lht;"
   )
   @Export("sentinel")
   CacheableNode sentinel;

   public Node2LinkedList() {
      this.sentinel = new CacheableNode();
      this.sentinel.previous = this.sentinel;
      this.sentinel.next = this.sentinel;
   }

   @ObfuscatedName("o")
   @ObfuscatedSignature(
      signature = "(Lht;)V"
   )
   @Export("push")
   public void push(CacheableNode var1) {
      if(var1.next != null) {
         var1.unlinkDual();
      }

      var1.next = this.sentinel.next;
      var1.previous = this.sentinel;
      var1.next.previous = var1;
      var1.previous.next = var1;
   }

   @ObfuscatedName("k")
   @ObfuscatedSignature(
      signature = "(Lht;)V"
   )
   @Export("setHead")
   public void setHead(CacheableNode var1) {
      if(var1.next != null) {
         var1.unlinkDual();
      }

      var1.next = this.sentinel;
      var1.previous = this.sentinel.previous;
      var1.next.previous = var1;
      var1.previous.next = var1;
   }

   @ObfuscatedName("d")
   @ObfuscatedSignature(
      signature = "()Lht;"
   )
   @Export("pop")
   CacheableNode pop() {
      CacheableNode var1 = this.sentinel.previous;
      if(var1 == this.sentinel) {
         return null;
      } else {
         var1.unlinkDual();
         return var1;
      }
   }

   @ObfuscatedName("h")
   @ObfuscatedSignature(
      signature = "()Lht;"
   )
   @Export("peek")
   public CacheableNode peek() {
      CacheableNode var1 = this.sentinel.previous;
      return var1 == this.sentinel?null:var1;
   }

   @ObfuscatedName("m")
   @Export("clear")
   void clear() {
      while(true) {
         CacheableNode var1 = this.sentinel.previous;
         if(var1 == this.sentinel) {
            return;
         }

         var1.unlinkDual();
      }
   }

   @ObfuscatedName("t")
   @ObfuscatedSignature(
      signature = "(Lht;Lht;)V"
   )
   static void method3860(CacheableNode var0, CacheableNode var1) {
      if(var0.next != null) {
         var0.unlinkDual();
      }

      var0.next = var1;
      var0.previous = var1.previous;
      var0.next.previous = var0;
      var0.previous.next = var0;
   }
}
