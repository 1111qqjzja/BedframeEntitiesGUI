package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.GeyserApi;

import java.util.HashMap;
import java.util.Map;

public final class BedrockAutoFormService {
    private BedrockAutoFormService() {
    }

    public static void open(ServerPlayerEntity player, InterceptedScreenModel model) {
        BedrockSessionStore.put(player.getUuid(), model);

        AutoFormKind chosen = AutoFormChooser.choose(model);
        if (chosen == AutoFormKind.SIMPLE) {
            openSimple(player, model);
        } else if (chosen == AutoFormKind.MODAL) {
            openModal(player, model);
        } else if (chosen == AutoFormKind.CUSTOM) {
            openCustom(player, model);
        } else {
            player.sendMessage(Text.literal("§c该界面无法自动转换为表单"), false);
        }
    }

    private static void openSimple(ServerPlayerEntity player, InterceptedScreenModel model) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(model.title())
                .content(model.content());

        for (ScreenOption option : model.options()) {
            if (option.imageType() != null && option.imageData() != null) {
                builder.button(
                        option.text(),
                        "url".equalsIgnoreCase(option.imageType()) ? FormImage.Type.URL : FormImage.Type.PATH,
                        option.imageData()
                );
            } else {
                builder.button(option.text());
            }
        }

        builder.validResultHandler(response -> {
            int idx = response.clickedButtonId();
            if (idx < 0 || idx >= model.options().size()) return;

            ScreenOption option = model.options().get(idx);

            System.out.println("[Bedframe] simple click source=" + model.sourceId() + ", option=" + option.id());

            if (BuiltinActionExecutor.handle(player, option.id())) return;
            if (ActionExecutorRegistry.handleOption(player, model.sourceId(), option.id())) return;

            player.sendMessage(Text.literal("§c按钮动作未实现: " + option.id()), false);
        });

        builder.closedResultHandler(() -> BedrockSessionStore.remove(player.getUuid()));

        GeyserApi.api().sendForm(player.getUuid(), builder);
    }

    private static void openModal(ServerPlayerEntity player, InterceptedScreenModel model) {
        if (model.options().size() != 2) {
            openSimple(player, model);
            return;
        }

        ScreenOption first = model.options().get(0);
        ScreenOption second = model.options().get(1);

        ModalForm.Builder builder = ModalForm.builder()
                .title(model.title())
                .content(model.content())
                .button1(first.text())
                .button2(second.text());

        builder.validResultHandler(response -> {
            String optionId = response.clickedFirst() ? first.id() : second.id();

            System.out.println("[Bedframe] modal click source=" + model.sourceId() + ", option=" + optionId);

            if (BuiltinActionExecutor.handle(player, optionId)) return;
            if (ActionExecutorRegistry.handleOption(player, model.sourceId(), optionId)) return;

            player.sendMessage(Text.literal("§cModal 动作未实现: " + optionId), false);
        });

        builder.closedResultHandler(() -> BedrockSessionStore.remove(player.getUuid()));

        GeyserApi.api().sendForm(player.getUuid(), builder);
    }

    private static void openCustom(ServerPlayerEntity player, InterceptedScreenModel model) {
        CustomForm.Builder builder = CustomForm.builder().title(model.title());

        for (ScreenField field : model.fields()) {
            if (field instanceof InputField input) {
                builder.input(
                        input.label(),
                        input.placeholder(),
                        input.defaultValue() == null ? "" : input.defaultValue()
                );
            } else if (field instanceof ToggleField toggle) {
                builder.toggle(toggle.label(), toggle.defaultValue());
            } else if (field instanceof DropdownField dropdown) {
                builder.dropdown(dropdown.label(), dropdown.options());
            }
        }

        builder.validResultHandler(response -> {
            Map<String, Object> values = new HashMap<>();

            for (ScreenField field : model.fields()) {
                if (field instanceof InputField input) {
                    values.put(input.id(), response.next());
                } else if (field instanceof ToggleField toggle) {
                    values.put(toggle.id(), response.next());
                } else if (field instanceof DropdownField dropdown) {
                    Object raw = response.next();

                    String mapped = "";
                    if (raw instanceof Number n) {
                        int index = n.intValue();
                        if (index >= 0 && index < dropdown.options().size()) {
                            mapped = dropdown.options().get(index);
                        }
                    } else if (raw != null) {
                        String s = String.valueOf(raw);
                        if (dropdown.options().contains(s)) {
                            mapped = s;
                        } else {
                            try {
                                int index = Integer.parseInt(s);
                                if (index >= 0 && index < dropdown.options().size()) {
                                    mapped = dropdown.options().get(index);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    values.put(dropdown.id(), mapped);
                }
            }

            System.out.println("[Bedframe] custom submit source=" + model.sourceId() + ", values=" + values);

            if (ActionExecutorRegistry.handleCustom(player, model.sourceId(), values)) return;

            player.sendMessage(Text.literal("§cCustomForm 提交未实现"), false);
        });

        builder.closedResultHandler(() -> {
            System.out.println("[Bedframe] custom closed source=" + model.sourceId());
            if (!ActionExecutorRegistry.handleOption(player, model.sourceId(), "action:back_to_selection")) {
                BedrockSessionStore.remove(player.getUuid());
            }
        });

        GeyserApi.api().sendForm(player.getUuid(), builder);
    }
}
