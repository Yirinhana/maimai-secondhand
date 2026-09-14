import cat from '../assets/avatars/cat.jpg';
import dog from '../assets/avatars/dog.jpg';
import capybara from '../assets/avatars/capybara.jpg';
import owl from '../assets/avatars/owl.jpg';

export const defaultAvatars = [
  { id: 'cat', name: '小橘猫', src: cat },
  { id: 'dog', name: '小柴犬', src: dog },
  { id: 'capybara', name: '水豚', src: capybara },
  { id: 'owl', name: '猫头鹰', src: owl },
] as const;

export function defaultAvatar(nickname: string): string {
  let hash = 0;
  for (const char of nickname)
    hash = (Math.imul(hash, 31) + char.codePointAt(0)!) >>> 0;
  return defaultAvatars[hash % defaultAvatars.length]!.src;
}
