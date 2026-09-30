package com.restor.magicka;

public interface ElementalBody {
    Element element();
    int action();
    int previousAction();
    float actionTime(float partial);
    default String modelId() { return Elementals.id(element(), this instanceof ElementalTitan ? "titan" : "elemental"); }
}
