package com.http2.api;

import java.util.List;

/** @author Stephen Durfey */
public class PostEntities {

  public List<Entity> getEntities() {
    return entities;
  }

  public void setEntities(List<Entity> entities) {
    this.entities = entities;
  }

  List<Entity> entities;
}
