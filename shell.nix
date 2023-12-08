{ pkgs ? import <nixpkgs> { } }:
let sbt_override = pkgs.sbt.override { jre = pkgs.jre8; };
in pkgs.mkShell {
  packages = [ pkgs.netcat ];
  nativeBuildInputs = with pkgs; [ scala sbt_override coursier ];
}
