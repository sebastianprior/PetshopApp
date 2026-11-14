type Props = {
  section: "terms" | "privacy" | "contact";
};

export function LegalScreen({ section }: Props) {
  if (section === "terms") {
    return (
      <div className="page-shell legal-shell">
        <div className="section-header">
          <h2>Términos y condiciones</h2>
        </div>
        <div className="legal-content">
          <p>
            Esta es una tienda de demostración creada con fines educativos. Al usarla, aceptás que
            las compras son simuladas y no generan ningún cargo real.
          </p>
          <h3>1. Uso del sitio</h3>
          <p>
            PetshopApp está pensada para mostrar el funcionamiento de un e-commerce: catálogo,
            carrito, checkout, cuenta de usuario y panel de administración. No está destinada a
            uso comercial real.
          </p>
          <h3>2. Cuentas de usuario</h3>
          <p>
            Sos responsable de mantener la confidencialidad de tu contraseña. Podés eliminar tu
            cuenta contactándonos en cualquier momento.
          </p>
          <h3>3. Compras y stock</h3>
          <p>
            El stock que ves en el catálogo es el que efectivamente se descuenta con cada compra
            simulada. Las devoluciones se gestionan desde "Mis pedidos".
          </p>
        </div>
      </div>
    );
  }

  if (section === "privacy") {
    return (
      <div className="page-shell legal-shell">
        <div className="section-header">
          <h2>Política de privacidad</h2>
        </div>
        <div className="legal-content">
          <p>
            Guardamos únicamente los datos necesarios para operar la tienda: tu email, nombre,
            contraseña (encriptada) y, si comprás, tu dirección de envío.
          </p>
          <h3>Qué datos guardamos</h3>
          <p>Email, nombre, historial de pedidos, devoluciones y reseñas que publiques.</p>
          <h3>Qué no hacemos</h3>
          <p>No vendemos ni compartimos tus datos con terceros. No usamos tu información para publicidad.</p>
          <h3>Tus derechos</h3>
          <p>Podés editar tu nombre y contraseña desde "Mi cuenta", o pedirnos que eliminemos tu cuenta.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="page-shell legal-shell">
      <div className="section-header">
        <h2>Contacto</h2>
      </div>
      <div className="legal-content">
        <p>¿Tenés alguna duda sobre tu pedido o la tienda? Escribinos:</p>
        <div className="contact-card">
          <div>
            <span>Email</span>
            <strong>soporte@petshopapp.demo</strong>
          </div>
          <div>
            <span>Horario de atención</span>
            <strong>Lunes a viernes, 9 a 18 hs</strong>
          </div>
          <div>
            <span>Devoluciones</span>
            <strong>Se gestionan desde "Mis pedidos"</strong>
          </div>
        </div>
      </div>
    </div>
  );
}
