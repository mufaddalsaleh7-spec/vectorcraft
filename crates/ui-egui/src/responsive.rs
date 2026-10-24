//! Viewport-aware sizing for compact, desktop-style workspaces.
//!
//! A phone in landscape still gets VectorCraft's normal menu / toolbar / canvas / panel
//! arrangement.  The compact mode only trims chrome and panel widths; it never substitutes a
//! separate mobile workspace.  It is based on the available viewport rather than a user-agent, so
//! rotation, split-screen and browser chrome changes take effect on the next frame.

use egui::{Rect, Ui, Vec2, vec2};

/// The layout density selected for the current viewport.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum Mode {
    #[default]
    Desktop,
    /// A short, wide viewport such as a phone held horizontally.
    CompactLandscape,
}

/// Widths and touch targets used by the panel dock.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct DockMetrics {
    pub default_width: f32,
    pub min_width: f32,
    pub max_width: f32,
    pub icon_column: f32,
    pub icon_button: f32,
    pub header: f32,
    pub tab_height: f32,
}

const COMPACT_MIN_WIDTH: f32 = 560.0;
const COMPACT_MAX_WIDTH: f32 = 1_100.0;
const COMPACT_MAX_HEIGHT: f32 = 500.0;

#[derive(Clone, Copy)]
struct InteractionMetrics {
    radius: f32,
    resize_side: f32,
    resize_corner: f32,
}

impl InteractionMetrics {
    fn get(interaction: &egui::style::Interaction) -> Self {
        Self {
            radius: interaction.interact_radius,
            resize_side: interaction.resize_grab_radius_side,
            resize_corner: interaction.resize_grab_radius_corner,
        }
    }

    fn apply(self, interaction: &mut egui::style::Interaction) {
        interaction.interact_radius = self.radius;
        interaction.resize_grab_radius_side = self.resize_side;
        interaction.resize_grab_radius_corner = self.resize_corner;
    }
}

fn enlarge_touch_targets(interaction: &mut egui::style::Interaction) {
    interaction.interact_radius = interaction.interact_radius.max(10.0);
    interaction.resize_grab_radius_side = interaction.resize_grab_radius_side.max(10.0);
    interaction.resize_grab_radius_corner = interaction.resize_grab_radius_corner.max(12.0);
}

/// Pick a density from the usable viewport in logical points (CSS pixels in the web build).
pub(crate) fn mode_for(rect: Rect) -> Mode {
    let size = rect.size();
    if size.x >= COMPACT_MIN_WIDTH && size.x <= COMPACT_MAX_WIDTH && size.y <= COMPACT_MAX_HEIGHT && size.x > size.y {
        Mode::CompactLandscape
    } else {
        Mode::Desktop
    }
}

pub(crate) fn mode(ctx: &egui::Context) -> Mode {
    mode_for(ctx.content_rect())
}

pub(crate) fn compact(ctx: &egui::Context) -> bool {
    mode(ctx) == Mode::CompactLandscape
}

/// Apply compact spacing to the root UI. Explicitly-sized professional controls keep their visual
/// size, while ordinary buttons and menu rows gain a slightly larger touch target.
pub(crate) fn begin_frame(ui: &mut Ui) -> Mode {
    let mode = mode(ui.ctx());
    let ctx = ui.ctx().clone();
    let interaction_id = egui::Id::new("compact-landscape-interaction-baseline");
    if mode == Mode::CompactLandscape {
        let spacing = ui.spacing_mut();
        spacing.item_spacing = vec2(4.0, 3.0);
        spacing.button_padding = vec2(5.0, 2.0);
        spacing.interact_size = vec2(spacing.interact_size.x.max(28.0), spacing.interact_size.y.max(28.0));

        // Enlarge the invisible response region, not the desktop-styled artwork. A 26–28 point
        // field or menu row therefore has an effective finger target around 46–48 points high.
        // Update the context too: dialogs, popups and floating panels create independent root UIs.
        let baseline = InteractionMetrics::get(&ctx.style().interaction);
        ctx.data_mut(|data| {
            if data.get_temp::<InteractionMetrics>(interaction_id).is_none() {
                data.insert_temp(interaction_id, baseline);
            }
        });
        ctx.global_style_mut(|style| enlarge_touch_targets(&mut style.interaction));
        enlarge_touch_targets(&mut ui.style_mut().interaction);
    } else if let Some(baseline) = ctx.data_mut(|data| data.remove::<InteractionMetrics>(interaction_id)) {
        // Rotation or resize back to a desktop viewport restores exactly the interaction radii the
        // desktop style had before compact mode, without disturbing other live style preferences.
        ctx.global_style_mut(|style| baseline.apply(&mut style.interaction));
        baseline.apply(&mut ui.style_mut().interaction);
    }
    mode
}

pub(crate) fn app_bar_height(ctx: &egui::Context) -> f32 {
    if compact(ctx) { 28.0 } else { crate::chrome::APP_BAR_HEIGHT }
}

pub(crate) fn control_bar_height(ctx: &egui::Context) -> f32 {
    if compact(ctx) { 30.0 } else { 34.0 }
}

pub(crate) fn document_tabs_height(ctx: &egui::Context, large: bool) -> f32 {
    if compact(ctx) {
        if large { 34.0 } else { 28.0 }
    } else if large {
        44.0
    } else {
        35.0
    }
}

pub(crate) fn status_bar_height(ctx: &egui::Context) -> f32 {
    if compact(ctx) { 22.0 } else { 24.0 }
}

pub(crate) fn hint_bar_height(ctx: &egui::Context) -> f32 {
    if compact(ctx) { 22.0 } else { 28.0 }
}

pub(crate) fn dock_metrics(ctx: &egui::Context) -> DockMetrics {
    if compact(ctx) {
        let width = ctx.content_rect().width();
        DockMetrics {
            default_width: (width * 0.28).clamp(208.0, 240.0),
            min_width: 196.0,
            max_width: (width * 0.32).clamp(220.0, 270.0),
            icon_column: 40.0,
            icon_button: 34.0,
            header: 28.0,
            tab_height: 31.0,
        }
    } else {
        DockMetrics { default_width: 300.0, min_width: 230.0, max_width: 520.0, icon_column: 38.0, icon_button: 30.0, header: 14.0, tab_height: 33.0 }
    }
}

/// Top of a panel popped out from the icon rail. In compact mode it starts just below the visible
/// top chrome instead of leaving the desktop-sized 110 point gap.
pub(crate) fn panel_flyout_top(ctx: &egui::Context, control_bar: bool) -> f32 {
    if compact(ctx) {
        app_bar_height(ctx) + if control_bar { control_bar_height(ctx) } else { 0.0 } + document_tabs_height(ctx, false) + 6.0
    } else {
        110.0
    }
}

/// Maximum scrollable body size for a modal on a short viewport.
pub(crate) fn modal_max_size(ctx: &egui::Context, margin: i8) -> Vec2 {
    // The ScrollArea is inside the window frame. Reserve both inner margins and a little room for
    // the frame/shadow so a dialog's outer rect, not only its scrolling body, stays on screen.
    let outer = f32::from(margin).max(0.0) * 2.0 + 16.0;
    let size = ctx.content_rect().size() - vec2(outer, outer);
    vec2(size.x.max(160.0), size.y.max(64.0))
}

#[cfg(test)]
mod tests {
    use egui::{Pos2, vec2};

    use super::*;

    fn screen(w: f32, h: f32) -> Rect {
        Rect::from_min_size(Pos2::ZERO, vec2(w, h))
    }

    #[test]
    fn representative_phone_landscapes_use_the_compact_desktop_workspace() {
        for (w, h) in [(740.0, 360.0), (852.0, 393.0), (915.0, 412.0), (667.0, 375.0)] {
            assert_eq!(mode_for(screen(w, h)), Mode::CompactLandscape, "{w} × {h}");
        }
        assert_eq!(mode_for(screen(393.0, 852.0)), Mode::Desktop, "portrait keeps the regular layout behind the rotate hint");
        assert_eq!(mode_for(screen(1280.0, 800.0)), Mode::Desktop);
        assert_eq!(mode_for(screen(1600.0, 450.0)), Mode::Desktop, "an ultra-wide desktop window is not a phone workspace");
    }

    #[test]
    fn compact_metrics_leave_room_for_the_canvas() {
        let ctx = egui::Context::default();
        for (w, h) in [(740.0, 360.0), (852.0, 393.0), (915.0, 412.0)] {
            let mut out = ctx.run(egui::RawInput { screen_rect: Some(screen(w, h)), ..Default::default() }, |_| {});
            out.textures_delta.clear();
            let m = dock_metrics(&ctx);
            // Single-column Tools (54 pt including its margin), the expanded dock and its icon rail
            // still leave a useful central document window at every target size.
            assert!(w - 54.0 - m.default_width - m.icon_column >= 430.0, "{w} × {h}: {m:?}");
            assert!(app_bar_height(&ctx) + document_tabs_height(&ctx, false) + status_bar_height(&ctx) + hint_bar_height(&ctx) <= 100.0);
            let dialog = modal_max_size(&ctx, 12);
            assert!(dialog.x + 40.0 <= w && dialog.y + 40.0 <= h, "dialog body {dialog:?} at {w} × {h}");
        }
    }

    /// A real whole-app frame at `size`, after fonts and retained UI state have settled.
    fn app_frame(app: &mut crate::VectorcraftApp, ctx: &egui::Context, size: (f32, f32), time: f64) -> egui::FullOutput {
        let raw = egui::RawInput { screen_rect: Some(screen(size.0, size.1)), time: Some(time), focused: true, ..Default::default() };
        let mut out = ctx.run_ui(raw, |ui| {
            app.logic(ui.ctx());
            app.ui(ui);
        });
        out.textures_delta.clear();
        out
    }

    fn compact_app(size: (f32, f32)) -> (crate::VectorcraftApp, egui::Context, egui::FullOutput) {
        use serde_json::json;
        use vectorcraft_engine::Session;

        let ctx = egui::Context::default();
        ctx.enable_accesskit();
        let mut app = crate::VectorcraftApp::new(Session::new(), crate::Services::default());
        app.run("file.new", json!({"width": 320, "height": 180})).unwrap();
        // First installs fonts, second marks them ready, third lays out stable retained panels.
        app_frame(&mut app, &ctx, size, 0.0);
        let ready = app_frame(&mut app, &ctx, size, 0.1);
        let settled = app_frame(&mut app, &ctx, size, 0.2);
        let out = if settled.platform_output.accesskit_update.is_some() { settled } else { ready };
        (app, ctx, out)
    }

    #[test]
    fn whole_editor_fits_the_three_phone_landscape_viewports() {
        for size in [(740.0, 360.0), (852.0, 393.0), (915.0, 412.0)] {
            let (app, _, out) = compact_app(size);
            let canvas = app.canvas_rect.expect("the real canvas is laid out");
            assert!(app.compact_layout, "{} × {} selects compact landscape", size.0, size.1);
            assert!(canvas.width() >= 400.0, "{} × {} canvas width: {canvas:?}", size.0, size.1);
            // App/document chrome, both status rows, the ruler and the document scroll bar stay
            // present; even the shortest target retains well over 60% of its height for editing.
            assert!(canvas.height() >= size.1 - 135.0, "{} × {} canvas height: {canvas:?}", size.0, size.1);
            assert!(canvas.left() >= 45.0 && canvas.right() <= size.0 - 220.0, "toolbar and expanded dock surround {canvas:?}");

            let nodes = out.platform_output.accesskit_update.as_ref().expect("an accessibility tree");
            for title in ["File", "Edit", "Object", "Type", "Select", "Effect", "View", "Window", "Help"] {
                let node = nodes
                    .nodes
                    .iter()
                    .map(|(_, node)| node)
                    .find(|node| node.label() == Some(title))
                    .unwrap_or_else(|| panic!("missing {title} at {size:?}"));
                let bounds = node.bounds().unwrap_or_else(|| panic!("{title} has no bounds"));
                assert!(bounds.x0 >= 0.0 && bounds.x1 <= f64::from(size.0), "{title} is clipped at {size:?}: {bounds:?}");
            }
        }
    }

    #[test]
    fn collapsing_either_side_resizes_the_real_canvas() {
        use serde_json::json;

        let size = (852.0, 393.0);
        let (mut app, ctx, _) = compact_app(size);
        let expanded = app.canvas_rect.expect("expanded canvas").width();

        assert_eq!(app.run("window.collapseToolbar", json!({"collapsed": true})).unwrap(), json!(true));
        app_frame(&mut app, &ctx, size, 0.3);
        let tools_collapsed = app.canvas_rect.expect("canvas after collapsing Tools").width();
        assert!(tools_collapsed >= expanded + 15.0, "{expanded} -> {tools_collapsed}");

        assert_eq!(app.run("window.collapseDock", json!({"collapsed": true})).unwrap(), json!(true));
        app_frame(&mut app, &ctx, size, 0.4);
        let both_collapsed = app.canvas_rect.expect("canvas after collapsing panels").width();
        assert!(both_collapsed >= tools_collapsed + 180.0, "{tools_collapsed} -> {both_collapsed}");

        app.run("window.collapseToolbar", json!({"collapsed": false})).unwrap();
        app.run("window.collapseDock", json!({"collapsed": false})).unwrap();
        app_frame(&mut app, &ctx, size, 0.5);
        let restored = app.canvas_rect.expect("restored canvas").width();
        assert!((restored - expanded).abs() <= 2.0, "{expanded} -> {restored}");
    }
}
