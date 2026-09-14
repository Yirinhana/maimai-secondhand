const fs=require('fs'),zlib=require('zlib');
function crc32(b){let c=0xffffffff;for(const x of b){c^=x;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return (c^0xffffffff)>>>0;}
function chunk(type,b){const t=Buffer.from(type),n=Buffer.alloc(4),crc=Buffer.alloc(4);n.writeUInt32BE(b.length);crc.writeUInt32BE(crc32(Buffer.concat([t,b])));return Buffer.concat([n,t,b,crc]);}
const ih=Buffer.alloc(13);ih.writeUInt32BE(8);ih.writeUInt32BE(8,4);ih[8]=8;ih[9]=2;
const raw=Buffer.alloc(8*25);for(let y=0;y<8;y++)for(let x=0;x<8;x++){const o=y*25+1+x*3;raw[o]=109;raw[o+1]=66;raw[o+2]=216;}
fs.writeFileSync('fixture8x8.png',Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
