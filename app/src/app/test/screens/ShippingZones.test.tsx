import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ShippingZonesSettings } from "../../screens/ShippingZonesSettings";
import { fetchShippingConfig, updateShippingConfig } from "../../api";
import type { ShippingConfig } from "../../types";

vi.mock("../../api", () => ({ fetchShippingConfig: vi.fn(), updateShippingConfig: vi.fn() }));
const mockedFetch = vi.mocked(fetchShippingConfig);
const mockedUpdate = vi.mocked(updateShippingConfig);

const CONFIG: ShippingConfig = {
  defaultCost: 2500,
  defaultEta: "4 a 7 días hábiles",
  zones: [
    { id: 1, name: "CABA", postalFrom: 1000, postalTo: 1499, cost: 1200, eta: "24 a 48 hs" },
    { id: 2, name: "GBA", postalFrom: 1500, postalTo: 1999, cost: 1500, eta: "2 a 3 días hábiles" },
  ],
};

describe("ShippingZonesSettings", () => {
  beforeEach(() => {
    mockedFetch.mockReset().mockResolvedValue(CONFIG);
    mockedUpdate.mockReset();
  });

  it("loads the current zones and the rest-of-the-country cost", async () => {
    render(<ShippingZonesSettings authToken="tok" />);

    expect(await screen.findByDisplayValue("CABA")).toBeInTheDocument();
    expect(screen.getByDisplayValue("GBA")).toBeInTheDocument();
    expect(screen.getByDisplayValue("2500")).toBeInTheDocument();
    expect(screen.getByDisplayValue("4 a 7 días hábiles")).toBeInTheDocument();
  });

  it("edits, adds and deletes zones and saves them as numbers", async () => {
    const user = userEvent.setup();
    mockedUpdate.mockImplementation(async (_token, config) => config);
    render(<ShippingZonesSettings authToken="tok" />);
    await screen.findByDisplayValue("CABA");

    // borra GBA, cambia el costo de CABA y agrega Córdoba
    await user.click(screen.getByRole("button", { name: "Borrar la zona 2" }));
    const cost = screen.getAllByLabelText("Costo ($)")[0];
    await user.clear(cost);
    await user.type(cost, "1300");
    await user.click(screen.getByText("Agregar zona"));
    await user.type(screen.getAllByLabelText("Nombre de la zona")[1], "Córdoba");
    await user.type(screen.getAllByLabelText("CP desde")[1], "5000");
    await user.type(screen.getAllByLabelText("CP hasta")[1], "5299");
    await user.type(screen.getAllByLabelText("Costo ($)")[1], "2000");
    await user.type(screen.getAllByLabelText("Plazo estimado")[2], "3 días");
    await user.click(screen.getByText("Guardar zonas de envío"));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith("tok", {
        defaultCost: 2500,
        defaultEta: "4 a 7 días hábiles",
        zones: [
          { name: "CABA", postalFrom: 1000, postalTo: 1499, cost: 1300, eta: "24 a 48 hs" },
          { name: "Córdoba", postalFrom: 5000, postalTo: 5299, cost: 2000, eta: "3 días" },
        ],
      }),
    );
    expect(await screen.findByText(/Cambios guardados/)).toBeInTheDocument();
  });

  it("shows the server error, for example when two zones overlap", async () => {
    const user = userEvent.setup();
    mockedUpdate.mockRejectedValue(new Error('Las zonas "CABA" y "GBA" se superponen en el código postal 1400.'));
    render(<ShippingZonesSettings authToken="tok" />);
    await screen.findByDisplayValue("CABA");

    await user.click(screen.getByText("Guardar zonas de envío"));

    expect(await screen.findByText(/se superponen/)).toBeInTheDocument();
  });

  it("explains that with no zones everything pays the rest-of-the-country cost", async () => {
    mockedFetch.mockResolvedValue({ ...CONFIG, zones: [] });
    render(<ShippingZonesSettings authToken="tok" />);

    expect(await screen.findByText(/No hay zonas/)).toBeInTheDocument();
  });
});
