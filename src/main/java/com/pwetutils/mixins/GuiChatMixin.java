package com.pwetutils.mixins;

import com.pwetutils.korean.HangulAssembler;
import com.pwetutils.listener.TextOverlayListener;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChat.class)
public class GuiChatMixin {
    @Shadow
    protected GuiTextField inputField;

    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private HangulAssembler.Hangul currentHangul = new HangulAssembler.Hangul();
    private boolean composing = false;

    @Inject(method = "initGui", at = @At("TAIL"))
    public void onInitGui(CallbackInfo ci) {
        TextOverlayListener.systemKoreanDetected = false;
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    public void onKeyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        boolean isSystemKorean = (typedChar >= 0xAC00 && typedChar <= 0xD7A3) || // complete korean syllables
                (typedChar >= 0x1100 && typedChar <= 0x11FF) || // korean jamo
                (typedChar >= 0x3130 && typedChar <= 0x318F);   // compatibility jamo

        if (isSystemKorean) {
            TextOverlayListener.systemKoreanDetected = true;
            if (TextOverlayListener.isKorean) {
                ci.cancel();
                return;
            }
        } else if (Character.isLetter(typedChar)) {
            TextOverlayListener.systemKoreanDetected = false;
        }

        if (!TextOverlayListener.isKorean) return;

        if (GuiScreen.isCtrlKeyDown() || GuiScreen.isKeyComboCtrlA(keyCode) ||
                GuiScreen.isKeyComboCtrlC(keyCode) || GuiScreen.isKeyComboCtrlV(keyCode) ||
                GuiScreen.isKeyComboCtrlX(keyCode)) {
            currentHangul.clear();
            composing = false;
            return;
        }

        if (!Character.isLetter(typedChar) && keyCode != 14) {
            currentHangul.clear();
            composing = false;
            return;
        }

        if (keyCode == 14) { // backspace
            if (composing && inputField.getCursorPosition() > 0) {
                String text = inputField.getText();
                int cursor = inputField.getCursorPosition();
                char prevChar = text.charAt(cursor - 1);

                HangulAssembler.Hangul h = HangulAssembler.decompose(prevChar);
                if (h.cho != -1) {
                    // if korean syllable, decompose it
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    inputField.setText(text);
                    inputField.setCursorPosition(cursor - 1);

                    if (h.jong != -1) {
                        // if complex jong
                        int simplifiedJong = HangulAssembler.decomposeComplexJong(h.jong);
                        if (simplifiedJong != -1) {
                            // complex jong: remove the last part
                            h.jong = simplifiedJong;
                            char newChar = h.toChar();
                            inputField.writeText(String.valueOf(newChar));
                            currentHangul = h;
                        } else {
                            // simple jong: remove it entirely
                            h.jong = -1;
                            char newChar = h.toChar();
                            inputField.writeText(String.valueOf(newChar));
                            currentHangul = h;
                        }
                    } else if (h.jung != -1) {
                        // remove jung, leave cho
                        char choChar = CHO.charAt(h.cho);
                        inputField.writeText(String.valueOf(choChar));
                        currentHangul.clear();
                        currentHangul.cho = h.cho;
                    } else {
                        // only cho left, already removed
                        currentHangul.clear();
                    }
                    composing = true;
                    ci.cancel();
                    return;
                }
            }

            currentHangul.clear();
            composing = false;
            return;
        }

        if (!Character.isLetter(typedChar)) {
            currentHangul.clear();
            composing = false;
            return;
        }

        if (Character.isLetter(typedChar)) {
            char lower = Character.toLowerCase(typedChar);
            String text = inputField.getText();
            int cursor = inputField.getCursorPosition();

            if (composing && cursor > 0 && shouldReplaceLastChar(lower)) {
                // only remove if modifying the same syllable
                text = text.substring(0, cursor - 1) + text.substring(cursor);
                inputField.setText(text);
                inputField.setCursorPosition(cursor - 1);
            }

            boolean handled = processKoreanInput(lower);
            if (handled) {
                ci.cancel();
            }
        }
    }

    private boolean shouldReplaceLastChar(char key) {
        // only replace if adding to current syllable
        if (currentHangul.cho != -1 && currentHangul.jung == -1) {
            // have consonant: check if this is a vowel
            return HangulAssembler.getJungIndex(key) != -1;
        } else if (currentHangul.cho != -1 && currentHangul.jung != -1 && currentHangul.jong == -1) {
            // have consonant+vowel: check if this is a final consonant OR combinable vowel
            int jungIdx = HangulAssembler.getJungIndex(key);
            if (jungIdx != -1) {
                // check if vowels can combine
                return HangulAssembler.combineVowels(currentHangul.jung, jungIdx) != -1;
            }
            return HangulAssembler.getJongIndex(key) > 0;
        } else if (currentHangul.cho != -1 && currentHangul.jung != -1 && currentHangul.jong != -1) {
            // have complete syllable: check if this is a vowel (for jong stealing) or combinable jong
            if (HangulAssembler.getJungIndex(key) != -1) return true;
            int jongIdx = HangulAssembler.getJongIndex(key);
            if (jongIdx > 0) {
                return HangulAssembler.combineJong(currentHangul.jong, jongIdx) != -1;
            }
        }
        return false;
    }

    private boolean processKoreanInput(char key) {
        int choIdx = HangulAssembler.getChoIndex(key);
        int jungIdx = HangulAssembler.getJungIndex(key);
        int jongIdx = HangulAssembler.getJongIndex(key);

        if (currentHangul.cho == -1) {
            // starting new character
            if (choIdx != -1) {
                currentHangul.cho = choIdx;
                char jamo = HangulAssembler.convertKeyToKorean(key);
                inputField.writeText(String.valueOf(jamo));
                composing = true;
                return true;
            } else if (jungIdx != -1) {
                // starting with vowel
                char jamo = HangulAssembler.convertKeyToKorean(key);
                inputField.writeText(String.valueOf(jamo));
                currentHangul.clear();
                composing = false;
                return true;
            }
        } else if (currentHangul.jung == -1) {
            // have consonant: expecting vowel
            if (jungIdx != -1) {
                currentHangul.jung = jungIdx;
                char korean = currentHangul.toChar();
                inputField.writeText(String.valueOf(korean));
                composing = true;
                return true;
            } else {
                // not a vowel, start new character
                composing = false;
                currentHangul.clear();
                return processKoreanInput(key);
            }
        } else if (currentHangul.jong == -1) {
            // have consonant+vowel: maybe add final consonant or combine vowels
            if (jungIdx != -1) {
                // check if can combine vowels
                int combinedJung = HangulAssembler.combineVowels(currentHangul.jung, jungIdx);
                if (combinedJung != -1) {
                    currentHangul.jung = combinedJung;
                    char korean = currentHangul.toChar();
                    inputField.writeText(String.valueOf(korean));
                    composing = true;
                    return true;
                } else {
                    // can't combine: start new syllable
                    composing = false;
                    currentHangul.clear();
                    return processKoreanInput(key);
                }
            } else if (jongIdx > 0) {
                currentHangul.jong = jongIdx;
                char korean = currentHangul.toChar();
                inputField.writeText(String.valueOf(korean));
                composing = true;
                return true;
            } else {
                // start new syllable
                composing = false;
                currentHangul.clear();
                return processKoreanInput(key);
            }
        } else {
            // complete syllable with jong
            if (jongIdx > 0) {
                // try to combine jong
                int combinedJong = HangulAssembler.combineJong(currentHangul.jong, jongIdx);
                if (combinedJong != -1) {
                    currentHangul.jong = combinedJong;
                    char korean = currentHangul.toChar();
                    inputField.writeText(String.valueOf(korean));
                    composing = true;
                    return true;
                }
            }

            if (jungIdx != -1) {
                // vowel typed after complete syllable: steal the jong
                int stolenCho = HangulAssembler.jongToCho(currentHangul.jong);
                if (stolenCho != -1) {
                    // remove jong from current syllable
                    currentHangul.jong = -1;
                    char korean = currentHangul.toChar();
                    inputField.writeText(String.valueOf(korean));

                    // start new syllable with stolen consonant
                    currentHangul.clear();
                    currentHangul.cho = stolenCho;
                    currentHangul.jung = jungIdx;
                    korean = currentHangul.toChar();
                    inputField.writeText(String.valueOf(korean));
                    composing = true;
                    return true;
                }
            }
            // start new syllable
            composing = false;
            currentHangul.clear();
            return processKoreanInput(key);
        }

        return false;
    }
}