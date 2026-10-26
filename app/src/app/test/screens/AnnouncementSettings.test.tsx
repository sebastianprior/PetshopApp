import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AnnouncementSettings } from "../../screens/AnnouncementSettings";
import { updateSiteSettings } from "../../api";
import type { SiteSettings } from "../../types";

vi.mock("../../api", () => ({ updateSiteSettings: vi.fn() }));

const mockedUpdate = vi.mocked(updateSiteSettings);

const SETTINGS: SiteSettings = {
  topbarItems: ["Envíos a todo el país", "6 cuotas"],
  announcementMode: "AUTO",
  announcementText: "",
};

describe("AnnouncementSettings", () => {
  beforeEach(() => {
    mockedUpdate.mockReset();
  });

  it("shows the current settings and hides the custom text unless the mode is CUSTOM", () => {
    render(<AnnouncementSettings authToken="tok" settings={SETTINGS} onSaved={vi.fn()} />);

    expect(screen.getByRole("textbox")).toHaveValue("Envíos a todo el país\n6 cuotas");
    expect(screen.queryByPlaceholderText(/Black Friday/)).not.toBeInTheDocument();
  });

  it("saves a custom announcement and reports it to the parent", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    const saved: SiteSettings = { topbarItems: ["Envíos"], announcementMode: "CUSTOM", announcementText: "Black Friday" };
    mockedUpdate.mockResolvedValue(saved);
    render(<AnnouncementSettings authToken="tok" settings={SETTINGS} onSaved={onSaved} />);

    await user.selectOptions(screen.getByLabelText("Barra de anuncio (verde)"), "CUSTOM");
    await user.type(screen.getByPlaceholderText(/Black Friday/), "  Black Friday  ");
    await user.clear(screen.getByLabelText(/Barra superior/));
    await user.type(screen.getByLabelText(/Barra superior/), "Envíos");
    await user.click(screen.getByText("Guardar cambios"));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
    expect(mockedUpdate).toHaveBeenCalledWith("tok", {
      topbarItems: ["Envíos"],
      announcementMode: "CUSTOM",
      announcementText: "Black Friday",
      topbarMarquee: false,
      announcementMarquee: false,
    });
    expect(screen.getByText(/Cambios guardados/)).toBeInTheDocument();
  });

  it("shows the backend error and does not notify the parent", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    mockedUpdate.mockRejectedValue(new Error("Escribí el texto del anuncio o elegí otro modo."));
    render(<AnnouncementSettings authToken="tok" settings={SETTINGS} onSaved={onSaved} />);

    await user.click(screen.getByText("Guardar cambios"));

    await waitFor(() => expect(screen.getByText("Escribí el texto del anuncio o elegí otro modo.")).toBeInTheDocument());
    expect(onSaved).not.toHaveBeenCalled();
  });

  it("lets the admin make the announcement and the top bar scroll, and sends it", async () => {
    const user = userEvent.setup();
    mockedUpdate.mockResolvedValue({ ...SETTINGS, topbarMarquee: true, announcementMarquee: true });
    render(<AnnouncementSettings authToken="tok" settings={SETTINGS} onSaved={vi.fn()} />);

    await user.click(screen.getByLabelText(/Mover los textos de la barra superior/));
    await user.click(screen.getByLabelText(/Mover el texto del anuncio/));
    await user.click(screen.getByText("Guardar cambios"));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith("tok", expect.objectContaining({ topbarMarquee: true, announcementMarquee: true })),
    );
  });

  it("starts with the current marquee setting and hides the announcement option when the bar is off", async () => {
    const user = userEvent.setup();
    render(<AnnouncementSettings authToken="tok" settings={{ ...SETTINGS, announcementMarquee: true }} onSaved={vi.fn()} />);

    expect(screen.getByLabelText(/Mover el texto del anuncio/)).toBeChecked();
    expect(screen.getByLabelText(/Mover los textos de la barra superior/)).not.toBeChecked();

    await user.selectOptions(screen.getByLabelText("Barra de anuncio (verde)"), "OFF");
    expect(screen.queryByLabelText(/Mover el texto del anuncio/)).not.toBeInTheDocument();
  });
});
