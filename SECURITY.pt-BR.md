<p align="center"><img src="docs/logo.png" alt="Stagebook" width="64"></p>

# Política de segurança

[English](SECURITY.md) · **Português (Brasil)**

## Versões com correção

Só a última versão em [GitHub Releases](https://github.com/julianoAffonso/stagebook/releases/latest) recebe correções. O app avisa quando sai uma versão nova.

## Como reportar uma falha

Não abra uma issue pública para um problema de segurança.

Reporte em particular pelo GitHub: abra a [aba Security](https://github.com/julianoAffonso/stagebook/security) e escolha **Report a vulnerability**. Só o mantenedor vê o relato.

Conte o que encontrou, a versão do app, o aparelho e a versão do Android, e os passos para repetir. Uma prova de conceito ajuda, mas não acesse dados ou aparelhos de outras pessoas para fazer uma.

## O que acontece depois

- Você recebe uma resposta em até uma semana.
- Confirmada a falha, a correção sai numa versão nova, e as novidades da versão falam dela.
- Seu nome entra no aviso de segurança, a não ser que prefira ficar anônimo.

## O que vale

O app Android, o editor no computador que ele serve na rede local, o compartilhamento com a banda (rede local, QR e links pela internet), inclusive os PDFs que viajam com a música (conferidos antes de serem guardados: cabeçalho de PDF de verdade, no máximo 8 MB e 300 páginas, sem senha, com um nome que o app mesmo gera, e só de quem já está na banda), os arquivos `.stagebook` e os backups, o servidor MCP deste repositório e os workflows do GitHub.

Como o editor no computador confia num computador, para você avaliar: o navegador pede para entrar e a pessoa autoriza no aparelho, comparando um código de 4 números. O navegador guarda então uma chave aleatória num cookie por 90 dias, renovado a cada visita, e um assistente ligado pelo MCP guarda a própria chave num arquivo que só aquele usuário lê. O aparelho guarda só o hash de cada chave, e remover um computador em Ajustes → Editar no computador corta o acesso na hora. A chave viaja em HTTP puro na rede local (o editor só responde a um endereço, não a um nome): use numa rede em que você confia.

Fica de fora: problemas do próprio Android, do software do fabricante do aparelho ou do firmware da interface de áudio USB.
