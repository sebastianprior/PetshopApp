package com.petshop.app.service;

import com.petshop.app.model.FaqItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Preguntas frecuentes de ejemplo con las que arranca la tienda (envíos, pagos, devoluciones, cuenta…).
 * Son solo el punto de partida: el administrador las edita desde el panel y desde ahí manda lo que se guarda.
 */
public final class FaqDefaults {

    private FaqDefaults() {}

    private static final String[][] ITEMS = {
            {"¿Cuánto tarda en llegar mi pedido?",
                    "Preparamos y despachamos tu pedido en 24 hs hábiles. Una vez despachado, llega en 2 a 5 días hábiles según tu zona. "
                            + "Si todavía no llegó, escribinos a soporte@petshopapp.demo con tu número de pedido."},
            {"¿Hacen envíos a todo el país?",
                    "Sí, enviamos a todo el país. Ingresás tu dirección, ciudad y código postal al finalizar la compra."},
            {"¿Cuánto cuesta el envío? ¿Hay envío gratis?",
                    "El envío tiene un costo que ves en el carrito antes de pagar. Cuando la tienda tiene activada la promo de envío gratis, "
                            + "en el carrito aparece una barra que te muestra cuánto te falta para conseguirlo."},
            {"¿Qué medios de pago aceptan? ¿Hay cuotas?",
                    "Podés pagar con tarjeta. Las compras tienen hasta 6 cuotas sin interés. El pago se confirma al finalizar la compra."},
            {"¿Cómo devuelvo un producto?",
                    "Entrá a \"Mis pedidos\", buscá el pedido y tocá \"Solicitar devolución\" en el producto. Indicá la cantidad y el motivo: "
                            + "nuestro equipo la revisa y te avisamos el resultado en la pestaña \"Mis devoluciones\"."},
            {"¿Tengo garantía? ¿Puedo cambiar un producto?",
                    "Todos los productos tienen 30 días de garantía. Si llegó con un defecto o no es lo que pediste, pedí la devolución "
                            + "desde \"Mis pedidos\" y lo resolvemos."},
            {"¿Cómo sigo el estado de mi pedido?",
                    "Entrá a \"Mis pedidos\" con tu cuenta: ahí ves cada compra con su estado (pendiente de pago, completada, enviada o cancelada)."},
            {"Mi pago quedó pendiente, ¿qué hago?",
                    "A veces el banco tarda en responder. Tu pedido queda reservado y lo confirmamos automáticamente cuando se acredita el pago. "
                            + "Si no se acredita, se cancela solo y se libera el stock; no se te cobra."},
            {"¿Necesito una cuenta para comprar?",
                    "Podés comprar como invitado, sin cuenta. Con una cuenta además guardás tus favoritos, ves tu historial en \"Mis pedidos\" "
                            + "y podés pedir devoluciones."},
            {"¿Cómo uso un cupón de descuento?",
                    "En el carrito, escribí el código en \"Código de cupón\" y tocá \"Aplicar\". El descuento se ve en el resumen antes de pagar. "
                            + "Cada cupón tiene sus condiciones: monto mínimo, vencimiento y usos disponibles."},
            {"¿Qué es el descuento del día (la ruleta)?",
                    "Una vez por día podés girar la ruleta de Salchi y te toca un descuento en una categoría. Se aplica en el carrito solo a los "
                            + "productos de esa categoría hasta la medianoche y se suma a tus cupones."},
            {"Olvidé mi contraseña, ¿cómo la recupero?",
                    "En \"Iniciar sesión\" tocá \"Olvidé mi contraseña\", ingresá tu email y te enviamos un código para elegir una nueva."},
            {"¿Cómo los contacto? ¿Cuál es el horario de atención?",
                    "Escribinos a soporte@petshopapp.demo. Atendemos de lunes a viernes de 9 a 18 hs."},
            {"¿Qué pasa si un producto no tiene stock?",
                    "Si un producto figura como \"Sin stock\" no se puede comprar por ahora. El stock se actualiza en tiempo real, "
                            + "así que volvé a mirar en unos días."},
    };

    public static List<FaqItem> items() {
        List<FaqItem> items = new ArrayList<>();
        for (int i = 0; i < ITEMS.length; i++) {
            items.add(new FaqItem(ITEMS[i][0], ITEMS[i][1], i));
        }
        return items;
    }
}
